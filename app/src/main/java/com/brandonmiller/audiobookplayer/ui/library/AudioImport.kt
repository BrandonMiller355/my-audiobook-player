package com.brandonmiller.audiobookplayer.ui.library

import android.net.Uri
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_FOLDER
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B
import com.brandonmiller.audiobookplayer.library.FolderScanner
import com.brandonmiller.audiobookplayer.library.M4B_EXTENSION
import com.brandonmiller.audiobookplayer.library.M4bReadResult
import com.brandonmiller.audiobookplayer.library.M4bReader
import com.brandonmiller.audiobookplayer.library.ScanResult

/** A picked folder or `.m4b`, read and ready to store — or why it will not be. */
sealed interface AudioImport {

    /**
     * Everything a book's audio needs in the database. [chapters] carry `audiobookId = 0`; whoever
     * stores them fills in the real id.
     *
     * Not a data class: [artwork] is a [ByteArray], whose identity equality would be a trap.
     */
    class Ready(
        val sourceUri: Uri,
        val sourceType: String,
        val title: String,
        val chapters: List<ChapterEntity>,
        val artwork: ByteArray?,
        /** Worth telling the user even though the audio was accepted, or null when there is nothing. */
        val notice: String? = null,
    ) : AudioImport

    /** The pick was refused and its grant already given back. [message] is for the user. */
    data class Refused(val message: String) : AudioImport
}

/**
 * Reads a picked folder or `.m4b` into an [AudioImport], holding the grant while it does and giving
 * it back if the pick is refused.
 *
 * One place for this because two screens add audio: the Library adds it as a new book, and the
 * Reader adds it to a book that so far is an ebook alone (`add-standalone-ebooks` design D3). Two
 * copies of the scan-and-refuse rules is how those two would come to disagree about what counts as
 * a book.
 *
 * Blocking: every call reads through the content resolver, and a scan can open up to 128 files.
 * Call it off the main thread.
 */
class AudioImporter(
    private val scanner: FolderScanner,
    private val m4bReader: M4bReader,
    private val permissions: UriPermissionHolder,
) {

    fun folder(treeUri: Uri): AudioImport {
        // Taken before scanning: without it the URI is only good for this one process.
        permissions.persist(treeUri)

        return when (val result = scanner.scan(treeUri)) {
            is ScanResult.Found -> AudioImport.Ready(
                sourceUri = treeUri,
                sourceType = SOURCE_TYPE_FOLDER,
                title = result.title,
                chapters = result.files.mapIndexed { index, file ->
                    ChapterEntity(
                        audiobookId = 0,
                        chapterIndex = index,
                        title = file.title,
                        mediaUri = file.uri.toString(),
                    )
                },
                artwork = null,
            )

            ScanResult.NoSupportedAudio -> {
                // Common when a series or parent folder is picked. Give the grant back rather than
                // leaking it for a book that was never added.
                permissions.release(treeUri)
                AudioImport.Refused(
                    "No supported audio files in that folder. If the audio is in a " +
                        "subfolder, pick that subfolder instead.",
                )
            }

            is ScanResult.Failed -> {
                permissions.release(treeUri)
                AudioImport.Refused("That folder could not be read: ${result.reason}")
            }
        }
    }

    /**
     * The single-file counterpart to [folder]. Chapters are parsed once, here, and stored — the
     * container is never re-read on a later launch (PRD §8, §23).
     */
    fun m4bFile(documentUri: Uri): AudioImport {
        // Taken before reading: without it the URI is only good for this one process.
        permissions.persist(documentUri)

        val name = m4bReader.displayName(documentUri)
        if (!name.endsWith(M4B_EXTENSION, ignoreCase = true)) {
            // Give the grant back rather than leaking it for a book that was never added, the same
            // discipline a folder with no audio gets.
            permissions.release(documentUri)
            return AudioImport.Refused(
                if (name.isBlank()) {
                    "Only .m4b files can be added this way."
                } else {
                    "Only .m4b files can be added this way, and “$name” is not one."
                },
            )
        }

        return when (val result = m4bReader.read(documentUri)) {
            is M4bReadResult.Read -> m4b(documentUri, result)

            is M4bReadResult.Failed -> {
                permissions.release(documentUri)
                AudioImport.Refused("That file could not be read: ${result.reason}")
            }
        }
    }

    companion object {
        /**
         * An `.m4b` already read, as an import. Separate from [m4bFile] because the bundled sample
         * arrives this way too, without a picker and without a grant to take.
         */
        fun m4b(documentUri: Uri, result: M4bReadResult.Read): AudioImport.Ready {
            val contents = result.contents
            return AudioImport.Ready(
                sourceUri = documentUri,
                sourceType = SOURCE_TYPE_M4B,
                title = contents.title,
                // Every chapter references the one file, distinguished by its start offset (PRD §19).
                chapters = contents.chapters.map { chapter ->
                    ChapterEntity(
                        audiobookId = 0,
                        chapterIndex = chapter.index,
                        title = chapter.title,
                        mediaUri = documentUri.toString(),
                        startPositionMs = chapter.startMs,
                        endPositionMs = chapter.endMs.takeIf { it > chapter.startMs },
                    )
                },
                artwork = result.artwork,
                // Said, not enforced: the audio is almost certainly fine, and refusing a playable
                // book over a malformed metadata box would be worse than losing its chapter marks
                // (`add-m4b-books` design D5).
                notice = if (contents.chaptersUnreadable) {
                    "Added, but this file's chapters could not be read, so it is one " +
                        "chapter covering the whole book."
                } else {
                    null
                },
            )
        }
    }
}
