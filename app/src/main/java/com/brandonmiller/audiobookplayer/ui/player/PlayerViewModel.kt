package com.brandonmiller.audiobookplayer.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.ChapterSummaryEntity
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.NoteEntity
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B
import com.brandonmiller.audiobookplayer.data.SpeedPreferences
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.ui.library.UriPermissionHolder
import com.brandonmiller.audiobookplayer.playback.BookTimeline
import com.brandonmiller.audiobookplayer.playback.PlaybackSample
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.chapterFinishedBy
import com.brandonmiller.audiobookplayer.playback.chapterTimeline
import com.brandonmiller.audiobookplayer.playback.currentLocation
import com.brandonmiller.audiobookplayer.playback.loadBook
import com.brandonmiller.audiobookplayer.playback.noteAnchorFor
import com.brandonmiller.audiobookplayer.playback.resolveChapterDurations
import com.brandonmiller.audiobookplayer.summaries.matchSummaries
import com.brandonmiller.audiobookplayer.summaries.parseSummaryFile
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val connected: Boolean = false,
    val bookId: Long = 0,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterNumber: Int = 0,
    val chapterCount: Int = 0,
    val isPlaying: Boolean = false,
    /** Absolute position across the whole book, for the book-wide scrubber (design D4). */
    val absolutePositionMs: Long = 0,
    /** Best-effort total — grows as more chapter durations resolve; see design D4. */
    val bookDurationMs: Long = 0,
    val speed: Float = 1.0f,
    /** Path to this book's cached cover, or null to show the placeholder. */
    val artworkPath: String? = null,
    /** The whole book's chapters, for the inline chapter sheet. Empty until the book has loaded. */
    val chapters: List<PlayerChapter> = emptyList(),
    /**
     * How much of the current chapter is left — what the sheet's current row shows in place of that
     * chapter's total length. Null when the chapter's end is not yet known.
     */
    val chapterRemainingMs: Long? = null,
    /** Whether this book has an ebook linked, which is what the cover icon's two forms show. */
    val hasEbook: Boolean = false,
    /** Set once after a successful link, so picking an ebook goes straight on into reading it. */
    val openReaderRequested: Boolean = false,
    /** How many marks and notes this book carries — the mark segment's sub-label (design D6). */
    val noteCount: Int = 0,
    /** Set when a mark has just been taken, and cleared once its confirmation has been shown. */
    val markTaken: MarkTaken? = null,
    /**
     * This book's imported summaries, by chapter index (`add-chapter-summaries` design D2).
     *
     * The map is also the import's report: its size against [chapterCount] is the "42 of 90 chapters"
     * the sheet states, so a successful import needs no message of its own and the figure survives
     * the app being closed (design D8).
     */
    val summaries: Map<Int, String> = emptyMap(),
    /** Set when playback has just carried the owner out of a chapter that has a summary (design D6). */
    val summaryPrompt: SummaryPrompt? = null,
    /**
     * Why the last import came to nothing, shown in the chapter sheet's own summaries section rather
     * than as a snackbar (design D8, revised).
     *
     * A snackbar cannot work here and device testing is what showed it: the only control that starts
     * an import lives inside the chapter sheet, and a `ModalBottomSheet` renders above the
     * `Scaffold` that hosts the snackbar — so the message was being displayed underneath the sheet
     * that triggered it, every time. The report belongs next to the control either way.
     */
    val summaryImportError: String? = null,
    val errorMessage: String? = null,
)

/** The chapter an end-of-chapter offer is about: which one, and what to call it. */
data class SummaryPrompt(val chapterIndex: Int, val chapterTitle: String)

/**
 * A mark the user has just taken, carried only long enough to send the Player to it.
 *
 * [noteId] is what makes the notes screen open this note for writing rather than merely listing it
 * (design D8), which is what keeps marking and writing up one gesture. [chapterTitle] is the
 * anchored chapter — what the note itself says, not where the user was standing when they tapped.
 */
data class MarkTaken(val noteId: Long, val chapterTitle: String)

/**
 * One row of the chapter sheet. [startMs] is absolute across the book, so selecting a chapter is
 * the same `seekToAbsolute` the scrubber already performs rather than a second seek path.
 */
data class PlayerChapter(
    val number: Int,
    val title: String,
    val startMs: Long,
    val durationMs: Long?,
)

class PlayerViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val speedPreferences: SpeedPreferences,
    private val ebooks: EbookSource,
    private val permissions: UriPermissionHolder,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null

    /**
     * Read eagerly via [resolveChapterDurations] when a book is opened — not from the live
     * playback [Timeline], which only resolves durations at roughly the pace of actual playback
     * (found during device testing; see design D4's revision). `null` entries are chapters not
     * yet resolved; [com.brandonmiller.audiobookplayer.playback.chapterTimeline] falls back to
     * the live timeline for those.
     */
    private var chapterDurationsMs: List<Long?> = emptyList()

    /**
     * The book's chapters as stored, kept so the Player can name the current one (design D6). Read
     * here rather than from `currentMediaItem.mediaMetadata.title`, which for a single-item `.m4b`
     * book is the book's name on every chapter. For a folder book this is the row the chapter came
     * from rather than a copy of it that made a round trip through the session, so what is
     * displayed does not change.
     */
    private var chapters: List<ChapterEntity> = emptyList()

    /**
     * The previous position sample, against which the next one is judged a chapter crossing or not
     * (`add-chapter-summaries` design D6). Null until the first sample, and again after any control
     * that moves the player — see [forgetLastSample].
     */
    private var lastSample: PlaybackSample? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            readPlayerState()
            if (isPlaying) startPositionUpdates() else positionJob?.cancel()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = readPlayerState()
        override fun onPlaybackStateChanged(playbackState: Int) = readPlayerState()

        // Chapter durations resolve asynchronously as Media3 reads each file's container
        // metadata; the scrubber's total should grow to reflect that (design D4).
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = readPlayerState()

        override fun onPlayerError(error: PlaybackException) {
            // A source that will not decode — a moved file, a revoked grant, or a zero-byte
            // placeholder — must not take the app down (PRD §22).
            _state.update { it.copy(errorMessage = "This chapter could not be played.") }
        }
    }

    init {
        connect()
        observeEbookLink()
        observeNoteCount()
        observeSummaries()
    }

    /**
     * Observed rather than read once, because an import happens with the chapter sheet open: the
     * rows have to gain their controls, and the count line has to change, without the sheet being
     * dismissed and reopened (`add-chapter-summaries` design D8).
     */
    private fun observeSummaries() {
        viewModelScope.launch {
            dao.observeChapterSummaries(bookId).collect { rows ->
                _state.update { state ->
                    state.copy(summaries = rows.associate { it.chapterIndex to it.text })
                }
            }
        }
    }

    /**
     * Reads a picked summary file, matches what it contains against this book's chapters, and
     * replaces whatever the book carried (design D1, D5).
     *
     * The file is finished with the moment this returns. No persistable grant is taken and no URI is
     * stored: a summary file is a few kilobytes fully consumed here, and keeping a reference would
     * mean re-parsing on every open of the chapter sheet and losing every summary the first time the
     * owner tidied a folder on the desktop.
     *
     * A successful import says nothing. It does not need to — the sheet's count line is showing the
     * result already, and unlike a message it is still there tomorrow (design D8). The three ways
     * this can come to nothing all speak up, because each one is something the owner has to act on.
     */
    fun importSummaries(uri: Uri) {
        viewModelScope.launch {
            // Whatever the last attempt said is no longer the news.
            _state.update { it.copy(summaryImportError = null) }

            val chapters = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }
            if (chapters.isEmpty()) {
                reportImportFailure(R.string.summaries_no_chapters)
                return@launch
            }

            val text = withContext(Dispatchers.IO) { readSummaryFile(uri) }
            if (text == null) {
                reportImportFailure(R.string.summaries_unreadable)
                return@launch
            }

            val entries = parseSummaryFile(text)
            if (entries.isEmpty()) {
                reportImportFailure(R.string.summaries_no_entries)
                return@launch
            }

            val match = matchSummaries(entries, chapters.associate { it.chapterIndex to it.title })
            if (match.matchedCount == 0) {
                _state.update {
                    it.copy(
                        summaryImportError =
                            appContext.getString(R.string.summaries_no_match, match.entryCount),
                    )
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                dao.replaceChapterSummaries(
                    bookId,
                    match.byChapterIndex.map { (chapterIndex, summary) ->
                        ChapterSummaryEntity(audiobookId = bookId, chapterIndex = chapterIndex, text = summary)
                    },
                )
            }
        }
    }

    private fun reportImportFailure(message: Int) {
        _state.update { it.copy(summaryImportError = appContext.getString(message)) }
    }

    /**
     * The file's text, or null if it could not be read at all.
     *
     * Decoded rather than validated: [String] construction from bytes substitutes the replacement
     * character for anything malformed instead of throwing, so a file saved in some other encoding
     * imports with a few mangled characters rather than failing outright. That is the better outcome
     * for text the owner can see and correct.
     *
     * [MAX_SUMMARY_FILE_BYTES] guards against a mis-pick rather than against a real summary file. The
     * picker offers every `text/plain` document on the device, and reading a multi-gigabyte one into
     * memory to discover it has no chapter markers is not a mistake worth making.
     */
    private fun readSummaryFile(uri: Uri): String? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val collected = ByteArrayOutputStream()
            val chunk = ByteArray(READ_CHUNK_BYTES)
            while (collected.size() < MAX_SUMMARY_FILE_BYTES) {
                val read = stream.read(chunk)
                if (read < 0) break
                collected.write(chunk, 0, minOf(read, MAX_SUMMARY_FILE_BYTES - collected.size()))
            }
            String(collected.toByteArray(), Charsets.UTF_8)
        }
    }.getOrNull()

    /**
     * Observed rather than counted once, so the mark segment's sub-label is right after a note is
     * taken here and after one is deleted on the notes screen — which sits on the back stack above
     * this Player rather than replacing it.
     */
    private fun observeNoteCount() {
        viewModelScope.launch {
            dao.observeNotes(bookId).collect { notes ->
                _state.update { it.copy(noteCount = notes.size) }
            }
        }
    }

    /**
     * Keeps the cover icon honest across the Reader's lifetime. Both screens sit on the back stack
     * together, so a link removed in the Reader has to reach a Player that was built before it.
     */
    private fun observeEbookLink() {
        viewModelScope.launch {
            dao.observeEbookUri(bookId).collect { uri ->
                _state.update { it.copy(hasEbook = uri != null) }
            }
        }
    }

    /**
     * Links a picked EPUB, or explains why it cannot be.
     *
     * The file is parsed rather than sniffed, because the three refusals the user can act on —
     * not an EPUB, protected, unreadable — are exactly what parsing distinguishes, and a link that
     * only fails later when the Reader opens is a link that looks like it worked.
     */
    fun linkEbook(uri: Uri) {
        viewModelScope.launch {
            val previous = withContext(Dispatchers.IO) { dao.findBook(bookId)?.ebookUri }

            val result = withContext(Dispatchers.IO) {
                permissions.persist(uri)
                ebooks.read(uri)
            }

            if (result !is EbookParseResult.Parsed) {
                // Give the grant straight back: holding one for a file the app has refused to use
                // would leave the user's permission list quietly wrong.
                withContext(Dispatchers.IO) { permissions.release(uri) }
                _state.update { it.copy(errorMessage = appContext.getString(messageFor(result))) }
                return@launch
            }

            withContext(Dispatchers.IO) {
                dao.linkEbook(bookId, uri.toString())
                previous?.takeIf { it != uri.toString() }?.let { permissions.release(it.toUri()) }
            }
            _state.update { it.copy(hasEbook = true, openReaderRequested = true) }
        }
    }

    private fun messageFor(result: EbookParseResult): Int = when (result) {
        is EbookParseResult.Encrypted -> R.string.ebook_encrypted
        is EbookParseResult.NotAnEpub -> R.string.ebook_not_an_epub
        else -> R.string.ebook_unreadable
    }

    fun consumeReaderRequest() {
        _state.update { it.copy(openReaderRequested = false) }
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                _state.update { it.copy(connected = true) }
                loadBook(connected)
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun loadBook(controller: MediaController) {
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { dao.findBook(bookId) } ?: return@launch
            val chapters = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }
            this@PlayerViewModel.chapters = chapters

            _state.update {
                it.copy(
                    bookId = bookId,
                    bookTitle = book.title,
                    chapterCount = chapters.size,
                    artworkPath = book.artworkPath,
                    hasEbook = book.ebookUri != null,
                )
            }

            val loaded = controller.loadBook(book, chapters, speedPreferences.lastUsedSpeed())
            if (loaded != null) {
                _state.update { it.copy(speed = loaded.speed) }

                chapterDurationsMs = List(loaded.mediaItems.size) { null }
                // A single-file book's boundaries are exact and already on its media item, so
                // there is nothing to resolve — and resolving would mean opening a multi-gigabyte
                // container to learn a figure that was stored at add time (design D2).
                if (book.sourceType != SOURCE_TYPE_M4B) resolveDurationsInBackground(loaded.mediaItems)
            }
            readPlayerState()
        }
    }

    /**
     * Reads every chapter's real duration off the container header, independent of playback, so
     * the scrubber's total reflects the whole book quickly rather than only converging as far as
     * the book has actually been played (design D4, revised after device testing).
     */
    private fun resolveDurationsInBackground(mediaItems: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            resolveChapterDurations(appContext, mediaItems) { index, durationMs ->
                // Up to maxConcurrency callbacks can land here at once; the mutation and the
                // subsequent read both have to happen on the same (single) thread or concurrent
                // read-modify-write copies of chapterDurationsMs lose each other's updates.
                withContext(Dispatchers.Main) {
                    chapterDurationsMs = chapterDurationsMs.toMutableList().apply { this[index] = durationMs }
                    readPlayerState()
                }
                storeResolvedDuration(index, durationMs)
            }
        }
    }

    /**
     * Keeps what this pass just read, so the library can show the book's total length and the next
     * open starts with an exact scrubber instead of resolving from scratch (design D3).
     *
     * A folder chapter is one whole file starting at zero, so its end is its duration. The index is
     * the media item's, which for a folder book is also the chapter's — the only book shape that
     * reaches here, since a single-file book's boundaries were parsed at add time and never need
     * resolving.
     */
    private suspend fun storeResolvedDuration(index: Int, durationMs: Long?) {
        val chapterId = chapters.getOrNull(index)?.id ?: return
        if (durationMs == null || durationMs <= 0) return
        withContext(Dispatchers.IO) { dao.updateChapterEnd(chapterId, durationMs) }
    }

    /** Player.Listener only fires on state-change events; playing position needs its own tick. */
    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                readPlayerState()
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun readPlayerState() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val location = player.currentLocation(timeline)
        val absolutePositionMs = timeline.absolutePosition(location)
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                chapterNumber = location.chapterIndex + 1,
                chapterTitle = chapters.getOrNull(location.chapterIndex)?.title.orEmpty(),
                absolutePositionMs = absolutePositionMs,
                bookDurationMs = timeline.totalDurationMs(),
                chapters = chapterRows(timeline),
                chapterRemainingMs = timeline.remainingInChapter(location),
            )
        }
        noteCrossing(player.isPlaying, location.chapterIndex, absolutePositionMs)
    }

    /**
     * Watches consecutive samples for a chapter finished under playback, and offers its summary
     * (`add-chapter-summaries` design D6, D7).
     *
     * **Nothing on this path touches the player.** It reads `isPlaying` and a position that was
     * computed for the UI anyway, and its only effect is a field on the state. The prompt fires when
     * the owner is most likely walking or driving, and anything that stopped or moved the audio
     * there would be a demand made at the worst possible moment.
     *
     * The offer is a moment rather than a queue: it is marked prompted as it is made, so a crossing
     * that happens with the Player off screen is simply not offered again. That is design D7's
     * position, not an oversight — the chapter list is the durable way back to any summary, and an
     * offer surfacing much later would be about a chapter two chapters ago.
     */
    private fun noteCrossing(isPlaying: Boolean, chapterIndex: Int, absolutePositionMs: Long) {
        val current = PlaybackSample(bookId, chapterIndex, absolutePositionMs)
        val finished = chapterFinishedBy(lastSample, current, isPlaying)
        lastSample = current
        val finishedIndex = finished ?: return

        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { dao.chapterSummary(bookId, finishedIndex) } ?: return@launch
            if (summary.prompted) return@launch

            withContext(Dispatchers.IO) { dao.markChapterSummaryPrompted(bookId, finishedIndex) }
            _state.update {
                it.copy(
                    summaryPrompt = SummaryPrompt(
                        chapterIndex = finishedIndex,
                        chapterTitle = chapters.getOrNull(finishedIndex)?.title.orEmpty(),
                    ),
                )
            }
        }
    }

    /**
     * Forgets the last sample, so the next boundary reached is not read as one the narration carried
     * the owner over.
     *
     * The magnitude test in [chapterFinishedBy] catches a deliberate move on its own in every case
     * but one: pressing next-chapter a few seconds before the chapter ends advances the index by one
     * and the position by less than the threshold, which is indistinguishable from listening through.
     * Rather than widen the threshold — which would start rejecting real crossings — every control
     * that moves the player says so here.
     */
    private fun forgetLastSample() {
        lastSample = null
    }

    fun consumeSummaryPrompt() {
        _state.update { it.copy(summaryPrompt = null) }
    }

    /**
     * The sheet's rows: the stored chapter titles paired with the extents the timeline computes.
     *
     * Built from the timeline rather than from the chapter rows alone because a folder book's
     * durations live only in the timeline until they have been resolved, and because the timeline
     * is the one thing that already agrees with the scrubber about where each chapter starts.
     *
     * The timeline reports a single placeholder chapter before anything has loaded; pairing
     * against [chapters] drops it, so the sheet shows nothing rather than one nameless row.
     */
    private fun chapterRows(timeline: BookTimeline): List<PlayerChapter> =
        timeline.chapterSpans().mapNotNull { span ->
            val chapter = chapters.getOrNull(span.chapterIndex) ?: return@mapNotNull null
            PlayerChapter(
                number = span.chapterIndex + 1,
                title = chapter.title,
                startMs = span.absoluteStartMs,
                durationMs = span.durationMs,
            )
        }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Drives the −1m/−10s/+10s/+1m buttons; rolls across chapter boundaries (design D2). */
    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val target = timeline.seekTarget(player.currentLocation(timeline), deltaMs)
        player.seekTo(target.mediaItemIndex, target.positionMs)
        forgetLastSample()
        readPlayerState()
    }

    fun previousChapter() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val target = timeline.previousChapterTarget(player.currentLocation(timeline))
        player.seekTo(target.mediaItemIndex, target.positionMs)
        forgetLastSample()
        readPlayerState()
    }

    fun nextChapter() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val target = timeline.nextChapterTarget(player.currentLocation(timeline)) ?: return
        player.seekTo(target.mediaItemIndex, target.positionMs)
        forgetLastSample()
        readPlayerState()
    }

    /** Called when the user releases the scrubber at an absolute book-wide position. */
    fun seekToAbsolute(absoluteMs: Long) {
        val player = controller ?: return
        val target = player.chapterTimeline(chapterDurationsMs).targetForAbsolute(absoluteMs)
        player.seekTo(target.mediaItemIndex, target.positionMs)
        forgetLastSample()
        readPlayerState()
    }

    /**
     * The footer's mark control: pauses, records the current spot, and opens the new note for
     * writing (design D6).
     *
     * **Pausing is the point, not a side effect.** Writing a note means dictating it or typing it,
     * and both compete with the narration — a dictation key held open against a playing audiobook
     * hears the book, not the user. Marking is therefore a deliberate stop rather than something
     * done without breaking stride, and the lead-in (design D5) is what makes stopping cheap: the
     * anchor is already fifteen seconds back, so the passage replays from before the interruption.
     *
     * The pause happens first and synchronously, so the audio stops the instant the control is hit
     * rather than whenever the insert returns. Position is unaffected either way — pausing does not
     * move the player, so the anchor computed just above stays correct.
     *
     * Playback is left paused afterwards. Coming back from the notes screen lands on the Player with
     * its play control under the thumb, which is a smaller surprise than audio restarting itself
     * while the user is still reading what they wrote.
     */
    fun mark() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val anchor = noteAnchorFor(
            timeline = timeline,
            from = player.currentLocation(timeline),
            chapterTitles = chapters.map { it.title },
        )
        player.pause()

        viewModelScope.launch {
            val note = NoteEntity(
                audiobookId = bookId,
                mediaItemIndex = anchor.target.mediaItemIndex,
                positionMs = anchor.target.positionMs,
                chapterTitle = anchor.chapterTitle,
                createdAt = System.currentTimeMillis(),
            )
            val noteId = withContext(Dispatchers.IO) { dao.insertNote(note) }
            _state.update { it.copy(markTaken = MarkTaken(noteId, anchor.chapterTitle)) }
        }
    }

    fun consumeMarkTaken() {
        _state.update { it.copy(markTaken = null) }
    }

    fun setSpeed(speed: Float) {
        val player = controller ?: return
        player.playbackParameters = PlaybackParameters(speed, 1.0f)
        _state.update { it.copy(speed = speed) }
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSpeed(bookId, speed)
            speedPreferences.setLastUsedSpeed(speed)
        }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        positionJob?.cancel()
        controller?.removeListener(listener)
        // Releases this connection only. The service keeps playing, which is the point.
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_MS = 500L

        /** Generous for chapter summaries, and small enough that a mis-picked file is refused early. */
        private const val MAX_SUMMARY_FILE_BYTES = 4 * 1024 * 1024

        private const val READ_CHUNK_BYTES = 8 * 1024

        fun factory(context: Context, bookId: String): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    PlayerViewModel(
                        appContext = appContext,
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        speedPreferences = SpeedPreferences(appContext),
                        ebooks = EbookSource(appContext.contentResolver),
                        permissions = UriPermissionHolder(appContext),
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }
}
