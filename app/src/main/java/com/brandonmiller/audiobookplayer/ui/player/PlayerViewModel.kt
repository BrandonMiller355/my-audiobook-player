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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.mediaIdBelongsTo
import com.brandonmiller.audiobookplayer.playback.mediaItemsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val connected: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterNumber: Int = 0,
    val chapterCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val errorMessage: String? = null,
)

class PlayerViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = readPlayerState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = readPlayerState()
        override fun onPlaybackStateChanged(playbackState: Int) = readPlayerState()

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
                startPositionUpdates()
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
                controller.setMediaItems(mediaItemsFor(book, chapters))
                controller.prepare()
            }
            readPlayerState()
        }
    }

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
        val duration = player.duration
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                chapterNumber = player.currentMediaItemIndex + 1,
                chapterTitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = if (duration > 0) duration else 0,
            )
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
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
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }
}
