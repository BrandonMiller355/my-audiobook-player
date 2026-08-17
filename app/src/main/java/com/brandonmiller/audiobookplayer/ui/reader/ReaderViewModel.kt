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
import com.brandonmiller.audiobookplayer.data.ReadingPreferences
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.ebook.ReadingPosition
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.ui.library.UriPermissionHolder
import kotlinx.coroutines.Dispatchers
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
    /** Set when the ebook has been unlinked, so the screen leaves rather than showing nothing. */
    val closed: Boolean = false,
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

    /**
     * Only play/pause is needed here, so only play/pause is listened for. The Reader deliberately
     * does not track position: nothing on this screen depends on where the audio is
     * (`add-ebook-companion` design D8).
     */
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }
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
                    val saved = ReadingPosition(
                        spineIndex = book.ebookSpineIndex ?: 0,
                        charOffset = book.ebookCharOffset ?: 0,
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            book = result.book,
                            scrollToBlock = result.book.blockIndexFor(saved),
                            unavailableMessage = null,
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

    /** Consumed by the screen once it has scrolled, so a recomposition does not scroll again. */
    fun consumeScrollTarget() {
        _state.update { it.copy(scrollToBlock = null) }
    }

    fun consumePickError() {
        _state.update { it.copy(pickErrorMessage = null) }
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

    fun jumpToBlock(blockIndex: Int) {
        _state.update { it.copy(scrollToBlock = blockIndex) }
        saveReadingPosition(blockIndex)
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
            _state.update {
                it.copy(
                    book = result.book,
                    scrollToBlock = 0,
                    unavailableMessage = null,
                    loading = false,
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
        controller?.removeListener(listener)
        // Releases this connection only. The service keeps playing, which is the point.
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
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
}
