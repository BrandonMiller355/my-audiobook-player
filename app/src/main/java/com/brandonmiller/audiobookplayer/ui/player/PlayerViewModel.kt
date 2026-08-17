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
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B
import com.brandonmiller.audiobookplayer.data.SpeedPreferences
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.ui.library.UriPermissionHolder
import com.brandonmiller.audiobookplayer.playback.BookTimeline
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.chapterTimeline
import com.brandonmiller.audiobookplayer.playback.currentLocation
import com.brandonmiller.audiobookplayer.playback.loadBook
import com.brandonmiller.audiobookplayer.playback.resolveChapterDurations
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
    val errorMessage: String? = null,
)

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
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                chapterNumber = location.chapterIndex + 1,
                chapterTitle = chapters.getOrNull(location.chapterIndex)?.title.orEmpty(),
                absolutePositionMs = timeline.absolutePosition(location),
                bookDurationMs = timeline.totalDurationMs(),
                chapters = chapterRows(timeline),
                chapterRemainingMs = timeline.remainingInChapter(location),
            )
        }
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
        readPlayerState()
    }

    fun previousChapter() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val target = timeline.previousChapterTarget(player.currentLocation(timeline))
        player.seekTo(target.mediaItemIndex, target.positionMs)
        readPlayerState()
    }

    fun nextChapter() {
        val player = controller ?: return
        val timeline = player.chapterTimeline(chapterDurationsMs)
        val target = timeline.nextChapterTarget(player.currentLocation(timeline)) ?: return
        player.seekTo(target.mediaItemIndex, target.positionMs)
        readPlayerState()
    }

    /** Called when the user releases the scrubber at an absolute book-wide position. */
    fun seekToAbsolute(absoluteMs: Long) {
        val player = controller ?: return
        val target = player.chapterTimeline(chapterDurationsMs).targetForAbsolute(absoluteMs)
        player.seekTo(target.mediaItemIndex, target.positionMs)
        readPlayerState()
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
