package com.brandonmiller.audiobookplayer.ui.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.LibraryBook
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_FOLDER
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B
import com.brandonmiller.audiobookplayer.library.CoverStore
import com.brandonmiller.audiobookplayer.library.FolderScanner
import com.brandonmiller.audiobookplayer.library.M4B_EXTENSION
import com.brandonmiller.audiobookplayer.library.M4bReadResult
import com.brandonmiller.audiobookplayer.library.M4bReader
import com.brandonmiller.audiobookplayer.library.SampleLibrary
import com.brandonmiller.audiobookplayer.library.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(
    private val dao: LibraryDao,
    private val scanner: FolderScanner,
    private val m4bReader: M4bReader,
    private val coverStore: CoverStore,
    private val permissions: UriPermissionHolder,
    private val sample: SampleLibrary,
) : ViewModel() {

    val books: StateFlow<List<LibraryBook>> = dao.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        .map { list -> list.filterNot { permissions.isHeld(Uri.parse(it.sourceUri)) }.map { it.id }.toSet() }
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
                        store(uri, result)
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

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                // Taken before scanning: without it the URI is only good for this one process.
                permissions.persist(treeUri)

                when (val result = withContext(Dispatchers.IO) { scanner.scan(treeUri) }) {
                    is ScanResult.Found -> {
                        val book = AudiobookEntity(
                            sourceUri = treeUri.toString(),
                            sourceType = SOURCE_TYPE_FOLDER,
                            title = result.title,
                            addedAt = System.currentTimeMillis(),
                        )
                        val chapters = result.files.mapIndexed { index, file ->
                            ChapterEntity(
                                audiobookId = 0,
                                chapterIndex = index,
                                title = file.title,
                                mediaUri = file.uri.toString(),
                            )
                        }
                        withContext(Dispatchers.IO) { dao.insertBookWithChapters(book, chapters) }
                    }

                    ScanResult.NoSupportedAudio -> {
                        // Common when a series or parent folder is picked. Give the grant back
                        // rather than leaking it for a book that was never added.
                        permissions.release(treeUri)
                        _message.value =
                            "No supported audio files in that folder. If the audio is in a " +
                            "subfolder, pick that subfolder instead."
                    }

                    is ScanResult.Failed -> {
                        permissions.release(treeUri)
                        _message.value = "That folder could not be read: ${result.reason}"
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * The single-file counterpart to [addFolder]. Chapters are parsed once, here, and stored — the
     * container is never re-read on a later launch (PRD §8, §23).
     */
    fun addM4bFile(documentUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                // Taken before reading: without it the URI is only good for this one process.
                permissions.persist(documentUri)

                val name = withContext(Dispatchers.IO) { m4bReader.displayName(documentUri) }
                if (!name.endsWith(M4B_EXTENSION, ignoreCase = true)) {
                    // Give the grant back rather than leaking it for a book that was never added,
                    // the same discipline a folder with no audio gets.
                    permissions.release(documentUri)
                    _message.value = if (name.isBlank()) {
                        "Only .m4b files can be added this way."
                    } else {
                        "Only .m4b files can be added this way, and “$name” is not one."
                    }
                    return@launch
                }

                when (val result = withContext(Dispatchers.IO) { m4bReader.read(documentUri) }) {
                    is M4bReadResult.Read -> {
                        store(documentUri, result)
                        if (result.contents.chaptersUnreadable) {
                            // Said, not enforced: the audio is almost certainly fine, and refusing
                            // a playable book over a malformed metadata box would be worse than
                            // losing its chapter marks (design D5).
                            _message.value =
                                "Added, but this file's chapters could not be read, so it is one " +
                                "chapter covering the whole book."
                        }
                    }

                    is M4bReadResult.Failed -> {
                        permissions.release(documentUri)
                        _message.value = "That file could not be read: ${result.reason}"
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun store(documentUri: Uri, result: M4bReadResult.Read) {
        val contents = result.contents
        val book = AudiobookEntity(
            sourceUri = documentUri.toString(),
            sourceType = SOURCE_TYPE_M4B,
            title = contents.title,
            addedAt = System.currentTimeMillis(),
        )
        // Every chapter references the one file, distinguished by its start offset (PRD §19).
        val chapters = contents.chapters.map { chapter ->
            ChapterEntity(
                audiobookId = 0,
                chapterIndex = chapter.index,
                title = chapter.title,
                mediaUri = documentUri.toString(),
                startPositionMs = chapter.startMs,
                endPositionMs = chapter.endMs.takeIf { it > chapter.startMs },
            )
        }

        withContext(Dispatchers.IO) {
            val bookId = dao.insertBookWithChapters(book, chapters)
            // After the insert, because the cover file is named for a book id that does not exist
            // until then. A book with no cover simply never gets a path.
            result.artwork
                ?.let { coverStore.write(bookId, it) }
                ?.let { path -> dao.updateArtworkPath(bookId, path) }
        }
    }

    fun remove(book: LibraryBook) {
        viewModelScope.launch {
            val sourceUri = Uri.parse(book.sourceUri)
            withContext(Dispatchers.IO) {
                dao.deleteBook(book.id)
                // Room's cascade covers rows, not files (design D7).
                coverStore.delete(book.id)
                // The one file removal may delete, and only because the app wrote it itself:
                // [SampleLibrary.delete] does nothing for a source it does not own, so a book the
                // user added is untouched on disk (design D9).
                sample.delete(sourceUri)
            }
            // Grants are a finite system-wide resource; leaking one per removed book eventually
            // breaks adding new ones, and the failure shows up much later looking unrelated.
            permissions.release(sourceUri)
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
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        scanner = FolderScanner(appContext.contentResolver),
                        m4bReader = M4bReader(appContext),
                        coverStore = CoverStore(appContext.filesDir),
                        permissions = UriPermissionHolder(appContext),
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
