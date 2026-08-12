package com.brandonmiller.audiobookplayer.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
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
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.SpeedPreferences
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.chapterTimeline
import com.brandonmiller.audiobookplayer.playback.currentLocation
import com.brandonmiller.audiobookplayer.playback.mediaIdBelongsTo
import com.brandonmiller.audiobookplayer.playback.mediaItemsFor
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
    val errorMessage: String? = null,
)

class PlayerViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val speedPreferences: SpeedPreferences,
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

            _state.update {
                it.copy(bookTitle = book.title, chapterCount = chapters.size)
            }

            // Reopening the Player for the book that is already loaded must not restart it from
            // zero, so only set the playlist when the controller is holding something else.
            if (!mediaIdBelongsTo(controller.currentMediaItem?.mediaId, bookId)) {
                // Clear playWhenReady first. Switching books while another one is playing would
                // otherwise inherit its "playing" state and start the new book by itself, which
                // is not what opening a book is supposed to do (PRD §6 ends its flow with the
                // user pressing play).
                controller.playWhenReady = false
                val mediaItems = mediaItemsFor(book, chapters)
                controller.setMediaItems(mediaItems)
                controller.prepare()

                val savedIndex = book.lastMediaItemIndex
                val savedPosition = book.lastPositionMs
                if (savedIndex != null && savedPosition != null && savedIndex < chapters.size) {
                    controller.seekTo(savedIndex, savedPosition)
                }

                val speed = book.playbackSpeed ?: speedPreferences.lastUsedSpeed()
                controller.playbackParameters = PlaybackParameters(speed, 1.0f)
                _state.update { it.copy(speed = speed) }

                chapterDurationsMs = List(mediaItems.size) { null }
                resolveDurationsInBackground(mediaItems)
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
            }
        }
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
                chapterTitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                absolutePositionMs = timeline.absolutePosition(location),
                bookDurationMs = timeline.totalDurationMs(),
            )
        }
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
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }
}
