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
import com.brandonmiller.audiobookplayer.data.ReadingPreferences
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.ebook.ReadingPosition
import com.brandonmiller.audiobookplayer.ebook.SearchHit
import com.brandonmiller.audiobookplayer.ebook.TextPosition
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.ReadAlongMap
import com.brandonmiller.audiobookplayer.playback.audioChaptersFrom
import com.brandonmiller.audiobookplayer.playback.chapterTimeline
import com.brandonmiller.audiobookplayer.playback.currentLocation
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
)

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

        val anchors = matchChapters(audioChaptersFrom(spans, titles), ebook, manualOffset = chapterOffset)
        return if (anchors.isEmpty()) null else ReadAlongMap(anchors)
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
                previous?.takeIf { it != uri.toString() }?.let { permissions.release(it.toUri()) }
            }
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
            withContext(Dispatchers.IO) {
                dao.unlinkEbook(bookId)
                previous?.let { permissions.release(it.toUri()) }
            }
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
