package com.brandonmiller.audiobookplayer.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/** What scanning a picked folder produced. */
sealed interface ScanResult {
    data class Found(val title: String, val files: List<ScannedFile>) : ScanResult

    /** The folder exists but holds nothing playable — often a series or parent folder. */
    data object NoSupportedAudio : ScanResult

    data class Failed(val reason: String) : ScanResult
}

data class ScannedFile(val title: String, val uri: Uri)

/**
 * Reads the audio files directly inside a folder chosen through the Storage Access Framework.
 *
 * Uses a single [ContentResolver] query rather than `DocumentFile.listFiles()`, which costs an
 * IPC round trip per child and then another per attribute — hundreds of them on the 128-file
 * books in this library, for data one cursor already carries.
 */
class FolderScanner(private val resolver: ContentResolver) {

    fun scan(treeUri: Uri): ScanResult = try {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val title = folderName(treeUri, treeDocumentId)
        val entries = readChildren(treeUri, treeDocumentId)
        val audio = FolderContents.audioFilesInOrder(entries)

        if (audio.isEmpty()) {
            ScanResult.NoSupportedAudio
        } else {
            ScanResult.Found(
                title = title,
                files = audio.map { entry ->
                    ScannedFile(
                        title = FolderContents.chapterTitle(entry.name),
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId),
                    )
                },
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not scan $treeUri", e)
        ScanResult.Failed(e.message ?: "the folder could not be read")
    }

    private fun readChildren(treeUri: Uri, treeDocumentId: String): List<ScannedEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        val entries = mutableListOf<ScannedEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                entries += ScannedEntry(
                    documentId = cursor.getString(idColumn) ?: continue,
                    name = name,
                    mimeType = cursor.getString(mimeColumn),
                )
            }
        }
        return entries
    }

    /** Falls back to the last path segment if the provider will not give a display name. */
    private fun folderName(treeUri: Uri, treeDocumentId: String): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return treeDocumentId.substringAfterLast('/').substringAfterLast(':').ifBlank { "Audiobook" }
    }

    private companion object {
        const val TAG = "FolderScanner"
    }
}
