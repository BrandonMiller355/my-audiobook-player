package com.brandonmiller.audiobookplayer.ui.reader

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.NoteEntity
import com.brandonmiller.audiobookplayer.data.ReadAlongCorrectionEntity
import com.brandonmiller.audiobookplayer.data.ReadingPreferences
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.ebook.ReadingPosition
import com.brandonmiller.audiobookplayer.ebook.SearchHit
import com.brandonmiller.audiobookplayer.ebook.TextPosition
import com.brandonmiller.audiobookplayer.playback.NUDGE_STEP_MS
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.ReadAlongAnchor
import com.brandonmiller.audiobookplayer.playback.ReadAlongCorrection
import com.brandonmiller.audiobookplayer.playback.ReadAlongMap
import com.brandonmiller.audiobookplayer.playback.anchorsWithCorrections
import com.brandonmiller.audiobookplayer.playback.audioChaptersFrom
import com.brandonmiller.audiobookplayer.playback.chapterTimeline
import com.brandonmiller.audiobookplayer.playback.correctionDeltaMs
import com.brandonmiller.audiobookplayer.playback.correctionFor
import com.brandonmiller.audiobookplayer.playback.currentLocation
import com.brandonmiller.audiobookplayer.playback.expressibleCorrectionRange
import com.brandonmiller.audiobookplayer.playback.matchChapters
import com.brandonmiller.audiobookplayer.playback.noteAnchorFor
import com.brandonmiller.audiobookplayer.ui.library.UriPermissionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val loading: Boolean = true,
    val book: Ebook? = null,
    /** The block to scroll to, set once when the saved position is restored and after a TOC jump. */
    val scrollToBlock: Int? = null,
    val settings: ReadingSettings = ReadingSettings(),
    val isPlaying: Boolean = false,
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    /**
     * Why the ebook cannot be shown, as a string resource, or null when it can. Distinct from
     * "loading" so the screen never shows an empty page that looks like a book with no words.
     */
    val unavailableMessage: Int? = null,
    /**
     * A rejected *pick*, which is a different thing from an unavailable *link* and must not be
     * shown the same way: the book already open is still perfectly readable, so this is a passing
     * message over it rather than a screen that replaces it.
     */
    val pickErrorMessage: Int? = null,
    /** The note a bookmark just created, carried only long enough to send the reader to it. */
    val markTaken: Long? = null,
    /** Set when the ebook has been unlinked, so the screen leaves rather than showing nothing. */
    val closed: Boolean = false,
    /**
     * The read-along map, or null when this book cannot support it — no chapter marks on the audio
     * side, or no table of contents on the ebook side. Null simply means the reader does not
     * follow: there is no control for it, so there is no state to explain to the user either.
     */
    val readAlongMap: ReadAlongMap? = null,
    /** Current playback position in ms, used to drive auto-scroll. */
    val playbackPositionMs: Long? = null,
    /**
     * How far the chapter being listened to has been corrected, in milliseconds of narration
     * (`add-readalong-nudge` design D4). Positive means the text has been pushed later.
     *
     * Zero for an uncorrected chapter, which is also what the stepper shows before the first nudge.
     * Only meaningful while [readAlongMap] is non-null, since that is the only time the control that
     * reads it is offered at all.
     */
    val correctionDeltaMs: Long = 0,
    /**
     * Set when the last nudge asked for more than the chapter could hold, so the sheet can say why
     * the figure stopped moving rather than letting the control look unresponsive.
     *
     * The room runs out near a chapter boundary, where there is little text left to redistribute.
     */
    val correctionAtLimit: Boolean = false,
    /**
     * The summary each table-of-contents entry carries, by `blockIndex` (`add-chapter-summaries`
     * design D10). Empty for a book with no summaries and for one whose chapters cannot be paired
     * with its entries — in both cases the contents sheet is what it was before this change.
     */
    val summariesByBlock: Map<Int, ChapterSummary> = emptyMap(),
)

/**
 * A summary and what to call the chapter it belongs to.
 *
 * The title is the *audio* chapter's, not the table-of-contents entry's, so that a summary is headed
 * the same way wherever it was opened from. This ebook's nav labels are bare ordinals — an entry
 * reading "3" would head the sheet "3", while the same summary reached from the Player's chapter
 * list would head it "Chapter 3".
 */
data class ChapterSummary(val chapterTitle: String, val text: String)

class ReaderViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val ebooks: EbookSource,
    private val permissions: UriPermissionHolder,
    private val preferences: ReadingPreferences,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var searchJob: Job? = null
    private var positionTrackingJob: Job? = null

    /**
     * The chapter-boundary anchors on their own, before any correction is merged in, and the map
     * they make (`add-readalong-nudge` design D6).
     *
     * Kept because every correction is measured and re-applied against the *uncorrected*
     * correspondence. Reading a delta back out of the corrected map and then re-applying it would
     * compound: each nudge would be relative to the previous nudge's result rather than to the
     * automatic matching, and the displayed figure would stop meaning "how far I have moved this
     * chapter" after the second tap.
     */
    private var baseAnchors: List<ReadAlongAnchor> = emptyList()
    private var baseMap: ReadAlongMap? = null

    /** Corrections as loaded, by audio chapter index — at most one per chapter (design D7). */
    private var corrections: MutableMap<Int, ReadAlongCorrection> = mutableMapOf()

    /** The burst being assembled: its frozen reference time, and the chapter it belongs to. */
    private var nudgeAnchorMs: Long? = null
    private var nudgeChapterIndex: Int? = null
    private var nudgeCommitJob: Job? = null

    /** Which chapter the pending write is for, so a burst that crosses a boundary cannot strand it. */
    private var pendingCommitChapter: Int? = null

    /**
     * The two halves of what the contents sheet's summary controls need, kept apart because they
     * arrive at different times and from different places (`add-chapter-summaries` design D10).
     *
     * The pairing is settled once, when the read-along map is built; the summaries are observed and
     * change under an import. [publishSummaries] joins them whenever either moves.
     */
    private var summaryChapterByBlock: Map<Int, Int> = emptyMap()
    private var summariesByChapter: Map<Int, String> = emptyMap()

    /** The audio chapters' own titles, for heading a summary the way the Player's list would. */
    private var chapterTitles: Map<Int, String> = emptyMap()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionTracking() else stopPositionTracking()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // A real move backward — a seek from anywhere, a chapter transition. The clamp exists
            // to reject the *unreal* ones, so it has to stand aside for this.
            releasePositionClamp()
        }
    }

    /**
     * The furthest the glide has followed the audio, and the floor it refuses to go below.
     *
     * `MediaController.currentPosition` extrapolates from the last position update using the
     * elapsed clock, and re-bases whenever a fresh update arrives from the session. The corrected
     * value can land slightly behind what had been extrapolated, so the reported position is not
     * monotonic even while playback runs straight forward. The glide turns position into a pixel
     * offset directly, so a regression of a few tens of milliseconds is the page scrolling backward
     * under a listener who did nothing. Held here rather than in the screen because this is a
     * property of the position source, not of the scroll that consumes it.
     *
     * [releasePositionClamp] drops the floor whenever the audio genuinely moves.
     */
    private var followedPositionMs = Long.MIN_VALUE

    private fun followedPosition(player: Player): Long {
        val reported = player.currentPosition
        if (reported < followedPositionMs) return followedPositionMs
        followedPositionMs = reported
        return reported
    }

    private fun releasePositionClamp() {
        followedPositionMs = Long.MIN_VALUE
    }

    private fun startPositionTracking() {
        if (positionTrackingJob?.isActive == true) return
        positionTrackingJob = viewModelScope.launch {
            while (true) {
                val pos = controller?.currentPosition
                if (pos != null) {
                    _state.update { it.copy(playbackPositionMs = pos) }
                }
                // Cheap, and it has to happen on some clock: playing on out of a corrected chapter
                // must stop the stepper reporting the previous chapter's figure.
                refreshCorrectionDelta()
                withContext(Dispatchers.Default) { kotlinx.coroutines.delay(250) }
            }
        }
    }

    private fun stopPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = null
    }

    init {
        load()
        observeSettings()
        observeSummaries()
        connect()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            preferences.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
    }

    /**
     * Connects for the sole purpose of the chrome's play/pause control. Note what is absent:
     * nothing here starts, stops, or seeks. Opening the Reader leaves playback exactly as it was.
     */
    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                _state.update { it.copy(isPlaying = connected.isPlaying) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, unavailableMessage = null) }

            val book = withContext(Dispatchers.IO) { dao.findBook(bookId) }
            val uri = book?.ebookUri?.toUri()
            if (uri == null) {
                _state.update { it.copy(loading = false, unavailableMessage = R.string.ebook_unreadable) }
                return@launch
            }

            when (val result = withContext(Dispatchers.IO) { ebooks.read(uri) }) {
                is EbookParseResult.Parsed -> {
                    val ebook = result.book
                    val saved = ReadingPosition(
                        spineIndex = book.ebookSpineIndex ?: 0,
                        charOffset = book.ebookCharOffset ?: 0,
                    )

                    _state.update {
                        it.copy(
                            loading = false,
                            book = ebook,
                            scrollToBlock = ebook.blockIndexFor(saved),
                            unavailableMessage = null,
                            readAlongMap = buildReadAlongMap(ebook, book.readAlongChapterOffset ?: 0),
                        )
                    }
                }

                is EbookParseResult.Encrypted ->
                    _state.update { it.copy(loading = false, unavailableMessage = R.string.ebook_encrypted) }

                is EbookParseResult.NotAnEpub ->
                    _state.update { it.copy(loading = false, unavailableMessage = R.string.ebook_not_an_epub) }

                is EbookParseResult.Unreadable ->
                    _state.update { it.copy(loading = false, unavailableMessage = R.string.ebook_unreadable) }
            }
        }
    }

    /**
     * The matcher pairs chapters by their *labels*, so it needs the book's real chapter titles. The
     * player's timeline carries starts and durations only — no titles — so they come from the
     * stored chapter rows, indexed by the same `chapterIndex` the timeline uses: its bounds are
     * built from those same rows, in that order (see `mediaItemsFor`).
     */
    private suspend fun buildReadAlongMap(ebook: Ebook, chapterOffset: Int = 0): ReadAlongMap? {
        if (ebook.contents.isEmpty()) return null

        val ctrl = controller ?: return null
        val spans = ctrl.chapterTimeline().chapterSpans()

        // One span covering the whole file is a book with no chapter marks, not a one-chapter book.
        if (spans.size <= 1) return null

        val titles = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }
            .associate { it.chapterIndex to it.title }
        chapterTitles = titles

        val anchors = matchChapters(audioChaptersFrom(spans, titles), ebook, manualOffset = chapterOffset)
        if (anchors.isEmpty()) return null

        baseAnchors = anchors
        baseMap = ReadAlongMap(anchors)
        summaryChapterByBlock = pairEntriesToChapters(ebook, anchors)
        publishSummaries()
        corrections = withContext(Dispatchers.IO) { dao.readAlongCorrections(bookId) }
            .associate { it.chapterIndex to ReadAlongCorrection(it.audioMs, it.charOffset) }
            .toMutableMap()

        return ReadAlongMap(anchorsWithCorrections(anchors, corrections.values.toList()))
    }

    /**
     * Which audio chapter each table-of-contents entry is, by `blockIndex` (`add-chapter-summaries`
     * design D10, revised).
     *
     * The design proposed inverting `matchChapters`'s result, which turns out not to be possible:
     * it returns anchors — `(absoluteMs, absoluteChars)` pairs — and discards which entry paired with
     * which chapter on the way out. The anchors are enough anyway, because of what they *are*: every
     * one is a matched chapter's start on both sides at once. Looking each anchor's `absoluteChars`
     * up against the entries recovers the pairing exactly, with no interpolation and no second
     * matching strategy.
     *
     * Using [baseAnchors] rather than the corrected map matters. An owner's read-along correction
     * adds an anchor mid-chapter (`add-readalong-nudge` design D1), and that anchor is not a chapter
     * start; matching against it would put a summary control on whatever entry happened to sit near
     * where the owner last nudged.
     *
     * An entry that matched no chapter is simply absent, which is what leaves front matter, part
     * headings, and sub-sections without a control.
     */
    /**
     * Observed, so a summary imported from the Player reaches a reader already sitting on the back
     * stack above it — the same reason [observeEbookLink] exists on the Player for the reverse trip.
     */
    private fun observeSummaries() {
        viewModelScope.launch {
            dao.observeChapterSummaries(bookId).collect { rows ->
                summariesByChapter = rows.associate { it.chapterIndex to it.text }
                publishSummaries()
            }
        }
    }

    /** Joins the entry-to-chapter pairing with the summaries themselves; either may arrive first. */
    private fun publishSummaries() {
        val byBlock = summaryChapterByBlock.mapNotNull { (blockIndex, chapterIndex) ->
            summariesByChapter[chapterIndex]?.let { summary ->
                blockIndex to ChapterSummary(chapterTitles[chapterIndex].orEmpty(), summary)
            }
        }.toMap()
        _state.update { it.copy(summariesByBlock = byBlock) }
    }

    private fun pairEntriesToChapters(ebook: Ebook, anchors: List<ReadAlongAnchor>): Map<Int, Int> {
        if (anchors.isEmpty()) return emptyMap()

        val chapterByChars = anchors.mapNotNull { anchor ->
            chapterIndexAt(anchor.absoluteMs)?.let { anchor.absoluteChars to it }
        }.toMap()

        return ebook.contents.mapNotNull { entry ->
            chapterByChars[ebook.absoluteCharsAt(entry.blockIndex)]?.let { entry.blockIndex to it }
        }.toMap()
    }

    /** Rebuilds the corrected map from the anchors already matched, without re-reading the book. */
    private fun remapWithCorrections(): ReadAlongMap? {
        if (baseAnchors.isEmpty()) return null
        return ReadAlongMap(anchorsWithCorrections(baseAnchors, corrections.values.toList()))
    }

    /** Which audio chapter [ms] falls in, or null when the timeline cannot say. */
    private fun chapterIndexAt(ms: Long): Int? {
        val spans = controller?.chapterTimeline()?.chapterSpans() ?: return null
        return spans.lastOrNull { it.absoluteStartMs <= ms }?.chapterIndex
    }

    /** Consumed by the screen once it has scrolled, so a recomposition does not scroll again. */
    fun consumeScrollTarget() {
        _state.update { it.copy(scrollToBlock = null) }
    }

    fun consumePickError() {
        _state.update { it.copy(pickErrorMessage = null) }
    }

    fun consumeMarkTaken() {
        _state.update { it.copy(markTaken = null) }
    }

    /**
     * Bookmarks the spot being read, from the reader's overflow menu. The Player's mark control in
     * every respect (`add-notes-and-bookmarks` design D6): it pauses, records, and opens the new
     * entry for writing on the notes screen.
     *
     * Anchored against the audio, not the text, because that is what a note stores (design D2) and
     * what makes an entry taken here interchangeable with one taken on the Player. For a read-along
     * book the two positions track each other anyway, so the audio anchor is the reading position.
     */
    fun bookmark() {
        val player = controller ?: return
        val timeline = player.chapterTimeline()
        val from = player.currentLocation(timeline)
        player.pause()

        viewModelScope.launch {
            val titles = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }.map { it.title }
            val anchor = noteAnchorFor(timeline, from, titles)
            val noteId = withContext(Dispatchers.IO) {
                dao.insertNote(
                    NoteEntity(
                        audiobookId = bookId,
                        mediaItemIndex = anchor.target.mediaItemIndex,
                        positionMs = anchor.target.positionMs,
                        chapterTitle = anchor.chapterTitle,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.update { it.copy(markTaken = noteId) }
        }
    }

    /**
     * Saves where the user is reading. Called when scrolling settles rather than per frame — a
     * write per scroll event would be hundreds of writes for one flick.
     */
    fun saveReadingPosition(firstVisibleBlock: Int) {
        val book = _state.value.book ?: return
        val position = book.positionOf(firstVisibleBlock)
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateReadingPosition(bookId, position.spineIndex, position.charOffset)
        }
    }

    /**
     * Runs off the main thread and cancels the previous run, because this is called on every
     * keystroke and a whole book is scanned each time.
     */
    fun search(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = query) }

        val book = _state.value.book ?: return
        searchJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.Default) { book.search(query) }
            _state.update { it.copy(searchHits = hits) }
        }
    }

    fun jumpToBlock(blockIndex: Int, alsoSeek: Boolean = true) {
        _state.update { it.copy(scrollToBlock = blockIndex) }
        if (alsoSeek && _state.value.readAlongMap != null) {
            // A chosen destination is the start of that block, so the fraction is zero — unlike a
            // settle, where the fraction is whatever the view came to rest on.
            seekToTextPosition(blockIndex, fraction = 0f, previousPositionMs = currentPositionMs() ?: 0L)
        }
        saveReadingPosition(blockIndex)
    }

    /**
     * Where the narration is in the text right now, to sub-block precision (design D6, D7).
     *
     * Reads the controller directly rather than the sampled [ReaderUiState.playbackPositionMs],
     * because this is called once per frame and the sample only refreshes four times a second.
     * `MediaController.currentPosition` extrapolates from the last position update using the
     * elapsed clock, so reading it per frame yields a continuously advancing value — which is what
     * lets the glide be smooth without polling the session any harder. [followedPosition] covers
     * the one way that value is not continuous.
     *
     * Fractional throughout: rounding either the character position or the position within the
     * block would put steps back into a target the glide samples sixty times a second.
     */
    fun currentTextPosition(): TextPosition? {
        val map = _state.value.readAlongMap ?: return null
        val book = _state.value.book ?: return null
        val ctrl = controller ?: return null
        return book.textPositionForAbsoluteChars(map.charsForMsExact(followedPosition(ctrl)))
    }

    /** Where playback is now, for capturing the position a settle-seek is about to move away from. */
    fun currentPositionMs(): Long? = controller?.currentPosition

    // ------------------------------------------------------------------ owner corrections

    /**
     * Moves the text one step earlier or later against the narration, for the chapter being
     * listened to (`add-readalong-nudge` design D3, D5, D6).
     *
     * Note what this does *not* do: it does not scroll the page and it does not touch the audio. It
     * replaces the map and lets the glide carry the page to the corrected position on its own. A
     * direct scroll would be undone within one frame — the glide chases `currentTextPosition()`
     * every frame and would simply pull the page back — and it would also read as user input to the
     * settle handler, which seeks. Going through the map avoids both, which is what lets this be a
     * plain pair of buttons rather than a mode that has to suppress the reader's other behavior.
     */
    fun nudge(steps: Int) {
        if (steps == 0) return
        val base = baseMap ?: return
        val ctrl = controller ?: return

        val now = ctrl.currentPosition
        val chapter = chapterIndexAt(now) ?: return

        // A burst is one adjustment: the reference time is frozen at its first tap, because the
        // audio carries on playing and a reference that moved would make each tap mean something
        // slightly different from the last. A tap in a different chapter starts a new burst.
        if (nudgeAnchorMs == null || nudgeChapterIndex != chapter) {
            nudgeAnchorMs = now
            nudgeChapterIndex = chapter
        }
        val anchorMs = nudgeAnchorMs ?: now

        val wanted = currentCorrectionDeltaMs(chapter) + steps * NUDGE_STEP_MS
        // What the chapter can actually hold. Near a boundary this collapses toward zero, and
        // accepting more than it would produce a segment the text sprints or crawls through.
        val room = expressibleCorrectionRange(baseAnchors, anchorMs) ?: return
        val delta = wanted.coerceIn(room)

        val correction = correctionFor(base, anchorMs, delta)
        corrections[chapter] = correction

        val remapped = remapWithCorrections()
        _state.update {
            it.copy(
                readAlongMap = remapped,
                correctionDeltaMs = correctionDeltaMs(base, correction),
                // Say so rather than letting the stepper appear to stop responding.
                correctionAtLimit = wanted != delta,
            )
        }

        scheduleCorrectionCommit(chapter)
    }

    /**
     * Writes the burst once it has stopped, rather than once per tap: a run of taps is one
     * adjustment, and a write each time would put a database round trip between a button and the
     * page moving. Replaces whatever the chapter carried before (design D7).
     *
     * A burst can change chapters underneath itself — the audio keeps playing while the owner taps,
     * so a run of taps near a chapter boundary can start in one chapter and finish in the next. The
     * outgoing chapter's correction is written *before* the new delay starts rather than having its
     * pending write cancelled: it is already applied to the map and visible on the page, and
     * cancelling the write would leave it showing until the next reload silently dropped it. That
     * divergence between what the reader shows and what the book carries is the one failure this
     * scheduling can produce, so it is closed here rather than documented.
     *
     * Both writes stay inside the single tracked job, so [cancelPendingNudge] still stops everything
     * a relink needs stopped.
     */
    private fun scheduleCorrectionCommit(chapter: Int) {
        val outgoing = pendingCommitChapter?.takeIf { it != chapter }
        nudgeCommitJob?.cancel()
        pendingCommitChapter = chapter
        nudgeCommitJob = viewModelScope.launch {
            outgoing?.let { commitCorrection(it) }
            kotlinx.coroutines.delay(NUDGE_COMMIT_DELAY_MS)
            commitCorrection(chapter)
            // The burst is over once it is written; the next tap freezes a fresh reference time.
            pendingCommitChapter = null
            nudgeAnchorMs = null
            nudgeChapterIndex = null
        }
    }

    private suspend fun commitCorrection(chapter: Int) {
        val correction = corrections[chapter] ?: return
        withContext(Dispatchers.IO) {
            dao.upsertReadAlongCorrection(
                ReadAlongCorrectionEntity(
                    audiobookId = bookId,
                    chapterIndex = chapter,
                    audioMs = correction.audioMs,
                    charOffset = correction.charOffset,
                ),
            )
        }
    }

    /**
     * Abandons a burst that has not been written yet, for when the thing it corrects is going away.
     *
     * Without this, a relink that lands inside the commit delay would clear the table and then have
     * the pending write put the old book's correction straight back into it.
     */
    private fun cancelPendingNudge() {
        nudgeCommitJob?.cancel()
        nudgeCommitJob = null
        pendingCommitChapter = null
        nudgeAnchorMs = null
        nudgeChapterIndex = null
    }

    /** How far the chapter has already been moved, which is what a further nudge adds to. */
    private fun currentCorrectionDeltaMs(chapter: Int): Long {
        val base = baseMap ?: return 0
        val correction = corrections[chapter] ?: return 0
        return correctionDeltaMs(base, correction)
    }

    /**
     * Keeps the stepper's figure describing the chapter actually being listened to, so that playing
     * on into an uncorrected chapter shows zero rather than the previous chapter's correction.
     */
    private fun refreshCorrectionDelta() {
        if (_state.value.readAlongMap == null) return
        val now = controller?.currentPosition ?: return
        val chapter = chapterIndexAt(now) ?: return
        val delta = currentCorrectionDeltaMs(chapter)
        if (delta != _state.value.correctionDeltaMs) {
            _state.update { it.copy(correctionDeltaMs = delta, correctionAtLimit = false) }
        }
    }

    private var lastSeekPreviousMs = 0L

    /**
     * Moves the audio to wherever the reader is now, given the text position the caller measured
     * from `layoutInfo` (design D6 — the caller owns that measurement because only the composable
     * can see rendered heights).
     *
     * Returns whether a seek actually happened: a move under the dead zone is not one (D5).
     */
    fun seekToTextPosition(blockIndex: Int, fraction: Float, previousPositionMs: Long): Boolean {
        val map = _state.value.readAlongMap ?: return false
        val book = _state.value.book ?: return false
        val ctrl = controller ?: return false

        val blockLength = book.blocks.getOrNull(blockIndex)?.text?.length ?: 0
        val chars = book.absoluteCharsAt(blockIndex) + (blockLength * fraction).toInt()
        val newPositionMs = map.msForChars(chars)

        if (kotlin.math.abs(newPositionMs - previousPositionMs) < SEEK_DEAD_ZONE_MS) return false

        val wasPlaying = ctrl.isPlaying
        ctrl.seekTo(newPositionMs)
        // Here as well as on the discontinuity callback, because that callback arrives a main-thread
        // pass later and the glide runs every frame: a backward seek left clamped even briefly would
        // hold the page where it was and then release it in a lurch.
        releasePositionClamp()
        lastSeekPreviousMs = previousPositionMs
        // D8: seek does not change transport state
        if (!wasPlaying && ctrl.isPlaying) ctrl.pause()
        return true
    }

    fun undoLastSeek() {
        val ctrl = controller ?: return
        ctrl.seekTo(lastSeekPreviousMs)
        releasePositionClamp()
    }

    companion object {
        private const val SEEK_DEAD_ZONE_MS = 3000L

        /**
         * How long after the last tap a burst is written. Long enough to cover the gap between
         * taps as the owner judges the result, short enough that leaving the reader immediately
         * after adjusting still saves it.
         */
        private const val NUDGE_COMMIT_DELAY_MS = 1_200L

        fun factory(context: Context, bookId: String): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    ReaderViewModel(
                        appContext = appContext,
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        ebooks = EbookSource(appContext.contentResolver),
                        permissions = UriPermissionHolder(appContext),
                        preferences = ReadingPreferences(appContext),
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Replaces the linked ebook. The reading position goes with the old one; it means nothing here. */
    fun changeEbook(uri: Uri) {
        viewModelScope.launch {
            val previous = withContext(Dispatchers.IO) { dao.findBook(bookId)?.ebookUri }

            val result = withContext(Dispatchers.IO) {
                permissions.persist(uri)
                ebooks.read(uri)
            }
            if (result !is EbookParseResult.Parsed) {
                withContext(Dispatchers.IO) { permissions.release(uri) }
                // The existing link is untouched — nothing was written — so the book on screen
                // stays on screen and the user is simply told the pick was refused.
                _state.update { it.copy(pickErrorMessage = messageFor(result)) }
                return@launch
            }

            withContext(Dispatchers.IO) {
                dao.linkEbook(bookId, uri.toString())
                // A correction is a character offset into one specific EPUB (design D10). Against a
                // different file it addresses an arbitrary place, so it goes with the book it was
                // made in — alongside the link itself, so no reload can observe the two disagreeing.
                dao.clearReadAlongCorrections(bookId)
                previous?.takeIf { it != uri.toString() }?.let { permissions.release(it.toUri()) }
            }
            corrections.clear()
            cancelPendingNudge()
            // Hits index the old book's blocks, so they would scroll to arbitrary places in the new
            // one. They go with the book they were found in.
            searchJob?.cancel()

            val book = withContext(Dispatchers.IO) { dao.findBook(bookId) }

            _state.update {
                it.copy(
                    book = result.book,
                    scrollToBlock = 0,
                    unavailableMessage = null,
                    loading = false,
                    searchQuery = "",
                    searchHits = emptyList(),
                    readAlongMap = buildReadAlongMap(result.book, book?.readAlongChapterOffset ?: 0),
                )
            }
        }
    }

    /** Removes the app's record of the ebook. The file itself is never touched. */
    fun unlinkEbook() {
        viewModelScope.launch {
            val previous = withContext(Dispatchers.IO) { dao.findBook(bookId)?.ebookUri }
            cancelPendingNudge()
            withContext(Dispatchers.IO) {
                dao.unlinkEbook(bookId)
                // As on a relink (design D10): they address a book that is no longer linked.
                dao.clearReadAlongCorrections(bookId)
                previous?.let { permissions.release(it.toUri()) }
            }
            corrections.clear()
            _state.update { it.copy(closed = true) }
        }
    }

    fun setTextScale(value: Float) = edit { preferences.setTextScale(value) }

    fun setLineSpacing(value: Float) = edit { preferences.setLineSpacing(value) }

    fun setSerif(serif: Boolean) = edit { preferences.setSerif(serif) }

    fun setBrightness(value: Float) = edit { preferences.setBrightness(value) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private fun messageFor(result: EbookParseResult): Int = when (result) {
        is EbookParseResult.Encrypted -> R.string.ebook_encrypted
        is EbookParseResult.NotAnEpub -> R.string.ebook_not_an_epub
        else -> R.string.ebook_unreadable
    }

    override fun onCleared() {
        stopPositionTracking()
        controller?.removeListener(listener)
        // Releases this connection only. The service keeps playing, which is the point.
        controller?.release()
        controller = null
        super.onCleared()
    }
}
