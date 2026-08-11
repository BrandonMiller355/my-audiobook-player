package com.brandonmiller.audiobookplayer.ui.library

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
import com.brandonmiller.audiobookplayer.library.FolderScanner
import com.brandonmiller.audiobookplayer.library.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val dao: LibraryDao,
    private val scanner: FolderScanner,
    private val permissions: UriPermissionHolder,
) : ViewModel() {

    val books: StateFlow<List<LibraryBook>> = dao.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Books whose folder permission is no longer held — the source was moved, deleted, or the
     * grant was revoked. They stay listed and removable rather than vanishing (PRD §22).
     */
    val unavailable: StateFlow<Set<Long>> = books
        .map { list -> list.filterNot { permissions.isHeld(Uri.parse(it.sourceUri)) }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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

    fun remove(book: LibraryBook) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteBook(book.id) }
            // Grants are a finite system-wide resource; leaking one per removed book eventually
            // breaks adding new ones, and the failure shows up much later looking unrelated.
            permissions.release(Uri.parse(book.sourceUri))
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
                        permissions = UriPermissionHolder(appContext),
                    ) as T
            }
        }
    }
}

/**
 * Takes and gives back the persistable read grants that let the library survive a reboot
 * (PRD §7.1, §16).
 */
class UriPermissionHolder(private val context: Context) {

    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

    fun persist(treeUri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
    }

    fun release(treeUri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(treeUri, flags) }
    }

    fun isHeld(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
}
