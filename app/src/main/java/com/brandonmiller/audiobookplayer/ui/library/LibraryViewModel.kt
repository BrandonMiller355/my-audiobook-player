package com.brandonmiller.audiobookplayer.ui.library

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.LibraryBook
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.SpeedPreferences
import com.brandonmiller.audiobookplayer.ebook.EbookParseResult
import com.brandonmiller.audiobookplayer.ebook.EbookSource
import com.brandonmiller.audiobookplayer.library.CoverStore
import com.brandonmiller.audiobookplayer.library.FolderScanner
import com.brandonmiller.audiobookplayer.library.M4bReadResult
import com.brandonmiller.audiobookplayer.library.M4bReader
import com.brandonmiller.audiobookplayer.library.SampleLibrary
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.loadBook
import com.brandonmiller.audiobookplayer.playback.mediaIdBelongsTo
import com.brandonmiller.audiobookplayer.playback.mediaIdBookId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val importer: AudioImporter,
    private val m4bReader: M4bReader,
    private val ebooks: EbookSource,
    private val coverStore: CoverStore,
    private val permissions: UriPermissionHolder,
    private val speedPreferences: SpeedPreferences,
    private val sample: SampleLibrary,
) : ViewModel() {

    val books: StateFlow<List<LibraryBook>> = dao.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The book the resume card offers: the most recently played of those with a saved position.
     *
     * A book with no total duration is still a valid target — it is resumable, it just cannot state
     * how much is left — so the filter is on having been played, not on being fully measured.
     */
    val resumeBook: StateFlow<LibraryBook?> = books
        .map { list -> list.filter { it.positionMs != null }.maxByOrNull { it.lastPlayedAt ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The book actually playing right now, or null when nothing is. */
    private val _playingBookId = MutableStateFlow<Long?>(null)

    /**
     * Whether the resume card's own book is the one currently playing.
     *
     * Scoped to that book deliberately: the card's control must not show as playing because some
     * other book is, which is exactly what a bare `isPlaying` would do after the user starts one
     * book and the card offers another.
     *
     * Combined rather than computed once, because either side moves independently — the session
     * starts and stops, and the library re-emits a different most-recently-played book.
     */
    val resumeIsPlaying: StateFlow<Boolean> = combine(resumeBook, _playingBookId) { book, playing ->
        book != null && book.id == playing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var controller: MediaController? = null

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = readPlaybackState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = readPlaybackState()
    }

    /**
     * Books whose source permission is no longer held — the folder or file was moved, deleted, or
     * the grant was revoked. They stay listed and removable rather than vanishing (PRD §22). A
     * document URI is not special here: it is taken, held, and checked exactly as a tree URI is.
     *
     * On IO because the check is not free: a persisted-grant lookup is a binder call, and for a
     * book in app-private storage it is a `stat` (design D8). Neither belongs on the main thread
     * once per book per library emission.
     */
    val unavailable: StateFlow<Set<Long>> = books
        .map { list ->
            // A book that is an ebook alone depends on its ebook's grant instead, since that is the
            // only source it has (`add-standalone-ebooks` design D4).
            list.filterNot { book ->
                val source = book.sourceUri ?: book.ebookUri
                source != null && permissions.isHeld(Uri.parse(source))
            }.map { it.id }.toSet()
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // Last, not first: `viewModelScope` dispatches on `Main.immediate`, so a coroutine launched
    // from an init block above these declarations can begin running before `_busy` exists.
    init {
        seedSampleOnce()
        connect()
    }

    /**
     * A second connection to the one session, so the resume card's control can start a book without
     * navigating to the Player (design D5). The service keeps playing regardless of this connection
     * — it is the same arrangement the Player has, not a second player.
     */
    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(playbackListener)
                readPlaybackState()
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun readPlaybackState() {
        val player = controller
        _playingBookId.value = if (player != null && player.isPlaying) {
            mediaIdBookId(player.currentMediaItem?.mediaId)
        } else {
            null
        }
    }

    /**
     * The resume card's play control. Toggles when the session is already holding this book;
     * otherwise loads it at its saved position and starts it.
     *
     * The load goes through the same [loadBook] the Player uses, rather than a copy of it — two
     * versions of "how to put a book into the session" is how the two screens end up disagreeing
     * about whether opening a book should start it (design D5).
     */
    fun toggleResumePlayback() {
        val player = controller ?: return
        val book = resumeBook.value ?: return

        viewModelScope.launch {
            if (mediaIdBelongsTo(player.currentMediaItem?.mediaId, book.id)) {
                if (player.isPlaying) player.pause() else player.play()
            } else {
                val entity = withContext(Dispatchers.IO) { dao.findBook(book.id) } ?: return@launch
                val chapters = withContext(Dispatchers.IO) { dao.chaptersFor(book.id) }
                player.loadBook(entity, chapters, speedPreferences.lastUsedSpeed())
                player.play()
            }
            readPlaybackState()
        }
    }

    override fun onCleared() {
        controller?.removeListener(playbackListener)
        // Releases this connection only. The service keeps playing, which is the point.
        controller?.release()
        controller = null
        super.onCleared()
    }

    /**
     * The app's own bundled book, added the first time the Library is ever opened — there is no
     * install-time hook for an app's own installation, and this is the earliest point that needs no
     * `Application` subclass (design D2).
     *
     * It goes in through the same [M4bReader] a picked file does, so it arrives with its chapters,
     * its duration, and its cover treatment identical to any other `.m4b` (design D3). No grant is
     * taken: the file is the app's own, in its own storage.
     */
    private fun seedSampleOnce() {
        viewModelScope.launch {
            if (sample.alreadySeeded()) return@launch

            _busy.value = true
            try {
                val uri = withContext(Dispatchers.IO) { sample.install() } ?: return@launch

                when (val result = withContext(Dispatchers.IO) { m4bReader.read(uri) }) {
                    is M4bReadResult.Read -> {
                        store(AudioImporter.m4b(uri, result))
                        // Only now: an interrupted seed should be retried on the next open, not
                        // recorded as done (design D5).
                        sample.markSeeded()
                    }

                    is M4bReadResult.Failed -> {
                        // Silent on purpose. The user did not ask for this book, so a failure to
                        // add it is not something to interrupt them about — and leaving the flag
                        // unset means the next open tries again.
                        sample.delete(uri)
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun addFolder(treeUri: Uri) = add { importer.folder(treeUri) }

    /** The single-file counterpart to [addFolder]. */
    fun addM4bFile(documentUri: Uri) = add { importer.m4bFile(documentUri) }

    private fun add(read: () -> AudioImport) {
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = withContext(Dispatchers.IO) { read() }) {
                    is AudioImport.Ready -> {
                        store(result)
                        result.notice?.let { _message.value = it }
                    }

                    is AudioImport.Refused -> _message.value = result.message
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun store(audio: AudioImport.Ready) {
        val book = AudiobookEntity(
            sourceUri = audio.sourceUri.toString(),
            sourceType = audio.sourceType,
            title = audio.title,
            addedAt = System.currentTimeMillis(),
        )

        withContext(Dispatchers.IO) {
            val bookId = dao.insertBookWithChapters(book, audio.chapters)
            // After the insert, because the cover file is named for a book id that does not exist
            // until then. A book with no cover simply never gets a path.
            audio.artwork
                ?.let { coverStore.write(bookId, it) }
                ?.let { path -> dao.updateArtworkPath(bookId, path) }
        }
    }

    /**
     * Adds an EPUB as a book of its own, with no audio (`add-standalone-ebooks` design D2). It opens
     * in the Reader, and gains its audio later from the Reader's menu.
     *
     * Parsed here rather than merely stored, for the same reason a linked ebook is parsed before the
     * link is written: a protected or malformed file is refused at the moment it is picked, with the
     * reason, rather than added and then found unreadable on first open. The parse also gives the
     * book its title.
     */
    fun addEbook(documentUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    permissions.persist(documentUri)
                    ebooks.read(documentUri)
                }
                if (result !is EbookParseResult.Parsed) {
                    permissions.release(documentUri)
                    // The same words a refused link gets in the Reader, from the same strings.
                    _message.value = appContext.getString(
                        when (result) {
                            is EbookParseResult.Encrypted -> R.string.ebook_encrypted
                            is EbookParseResult.NotAnEpub -> R.string.ebook_not_an_epub
                            else -> R.string.ebook_unreadable
                        },
                    )
                    return@launch
                }

                // An EPUB with no title in its package falls back to its file name, as a folder
                // book falls back to its folder's.
                val title = result.book.title.ifBlank {
                    withContext(Dispatchers.IO) { m4bReader.displayName(documentUri) }
                        .substringBeforeLast('.')
                        .ifBlank { appContext.getString(R.string.library_untitled_ebook) }
                }
                withContext(Dispatchers.IO) {
                    dao.insertBook(
                        AudiobookEntity(
                            sourceUri = null,
                            sourceType = null,
                            title = title,
                            addedAt = System.currentTimeMillis(),
                            ebookUri = documentUri.toString(),
                        ),
                    )
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun remove(book: LibraryBook) {
        viewModelScope.launch {
            val sourceUri = book.sourceUri?.let(Uri::parse)
            withContext(Dispatchers.IO) {
                dao.deleteBook(book.id)
                // Room's cascade covers rows, not files (design D7).
                coverStore.delete(book.id)
                // The one file removal may delete, and only because the app wrote it itself:
                // [SampleLibrary.delete] does nothing for a source it does not own, so a book the
                // user added is untouched on disk (design D9).
                sourceUri?.let(sample::delete)
            }
            // Grants are a finite system-wide resource; leaking one per removed book eventually
            // breaks adding new ones, and the failure shows up much later looking unrelated. The
            // ebook's grant goes too — for a book that is an ebook alone it is the only one it holds.
            sourceUri?.let(permissions::release)
            book.ebookUri?.let { permissions.release(Uri.parse(it)) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    LibraryViewModel(
                        appContext = appContext,
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        importer = AudioImporter(
                            scanner = FolderScanner(appContext.contentResolver),
                            m4bReader = M4bReader(appContext),
                            permissions = UriPermissionHolder(appContext),
                        ),
                        m4bReader = M4bReader(appContext),
                        ebooks = EbookSource(appContext.contentResolver),
                        coverStore = CoverStore(appContext.filesDir),
                        permissions = UriPermissionHolder(appContext),
                        speedPreferences = SpeedPreferences(appContext),
                        sample = SampleLibrary(appContext),
                    ) as T
            }
        }
    }
}

/**
 * Takes and gives back the persistable read grants that let the library survive a reboot
 * (PRD §7.1, §16). A folder's tree URI and a single file's document URI are handled identically —
 * the same read flag applies to both, so neither is special here (design D3).
 *
 * A `file://` source is the one thing that is different, and it is different in kind rather than
 * by exception: the app's own bundled sample lives in app-private storage, where there is no grant
 * to take, hold, or give back. Reachability is answered by asking whether the file is still there
 * (design D8).
 */
class UriPermissionHolder(private val context: Context) {

    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

    fun persist(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun release(uri: Uri) {
        if (uri.isLocalFile) return
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    /**
     * Whether this book's source can still be read. For a picked folder or file that means the
     * persisted grant; for a file in app-private storage it means the file exists — which is also
     * what makes a sample whose copy has gone missing list as unavailable rather than crash.
     */
    fun isHeld(uri: Uri): Boolean =
        if (uri.isLocalFile) {
            uri.path?.let { File(it).exists() } == true
        } else {
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        }
}

private val Uri.isLocalFile: Boolean get() = scheme == ContentResolver.SCHEME_FILE
