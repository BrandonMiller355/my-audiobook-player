package com.brandonmiller.audiobookplayer.library

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.brandonmiller.audiobookplayer.m4b.Chapter
import com.brandonmiller.audiobookplayer.m4b.ChapterParseResult
import com.brandonmiller.audiobookplayer.m4b.M4bChapterParser
import java.io.FileInputStream

/** The extension decides, not the MIME type the provider reports (design D4). */
const val M4B_EXTENSION = ".m4b"

/**
 * Everything an `.m4b` book needs at add time, whichever way its chapter data turned out.
 *
 * [chaptersUnreadable] is what separates "this file carries no chapter marks", which is normal and
 * silent, from "its chapter data could not be read", which is worth telling the user about. Both
 * produce the same one-chapter book (design D5): the audio is almost certainly fine either way,
 * and refusing a playable book over a malformed metadata box would be the worse outcome.
 */
data class M4bContents(
    val title: String,
    val durationMs: Long,
    val chapters: List<Chapter>,
    val chaptersUnreadable: Boolean,
)

/** What reading a picked `.m4b` produced. */
sealed interface M4bReadResult {

    /** Not a data class: [artwork] is a [ByteArray], whose identity equality would be a trap. */
    class Read(val contents: M4bContents, val artwork: ByteArray?) : M4bReadResult

    /** The file itself could not be opened — not merely its chapters. */
    data class Failed(val reason: String) : M4bReadResult
}

/**
 * Reads a single `.m4b` chosen through the Storage Access Framework: its name, its chapter marks,
 * its duration, and its embedded cover art.
 *
 * The counterpart to [FolderScanner] for the other book type, and the only caller of
 * [M4bChapterParser] — which until now was working, tested, and unreached.
 */
class M4bReader(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * The provider's display name, falling back to the URI's own last segment when it will not
     * give one. Callers use this to decide whether the picked file is an `.m4b` at all, so it is
     * read on its own before the much more expensive full [read].
     */
    fun displayName(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
    }

    fun read(uri: Uri): M4bReadResult {
        val name = displayName(uri)
        val parseResult = parseChapters(uri)
            ?: return M4bReadResult.Failed("the file could not be opened")

        // One retriever pass for both: each one opens the file through the document provider, and
        // this is a multi-gigabyte container.
        val source = readSourceMetadata(uri)

        return M4bReadResult.Read(
            contents = contentsFrom(
                title = titleFrom(name),
                parseResult = parseResult,
                fileDurationMs = source.durationMs,
            ),
            artwork = source.artwork,
        )
    }

    /**
     * `moov` can sit after a multi-gigabyte `mdat`, so the parser needs to seek; a [FileInputStream]
     * over the descriptor's channel gives it that (design D8). Some providers hand back a pipe
     * instead, which cannot seek — that needs no handling here, because [M4bChapterParser] already
     * reports it as [ChapterParseResult.Unreadable] and an unreadable book is added anyway.
     */
    private fun parseChapters(uri: Uri): ChapterParseResult? = try {
        resolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                M4bChapterParser.parse(stream.channel)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not open $uri", e)
        null
    }

    private class SourceMetadata(val durationMs: Long?, val artwork: ByteArray?)

    private fun readSourceMetadata(uri: Uri): SourceMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            SourceMetadata(
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 },
                artwork = retriever.embeddedPicture,
            )
        } catch (e: Exception) {
            // Neither figure is worth failing the add over: a book with an unknown duration still
            // plays, and a book with no cover shows the placeholder (PRD §22).
            Log.w(TAG, "could not read metadata from $uri", e)
            SourceMetadata(durationMs = null, artwork = null)
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val TAG = "M4bReader"
    }
}

/** The file name without its extension (PRD §11's title fallback). */
internal fun titleFrom(displayName: String): String =
    displayName.substringBeforeLast('.').ifBlank { displayName }.ifBlank { "Audiobook" }

/**
 * The one place the parser's three outcomes become a book.
 *
 * Kept free of Android types so it can be tested directly against synthetic parse results — the
 * chaptered path in particular cannot be exercised on the owner's device, where every `.m4b`
 * parses as unchaptered.
 */
internal fun contentsFrom(
    title: String,
    parseResult: ChapterParseResult,
    fileDurationMs: Long?,
): M4bContents = when (parseResult) {
    is ChapterParseResult.Chapters -> M4bContents(
        title = title,
        // The parser chains ends from the next start and takes the last from `mvhd`, so the final
        // chapter's end is the file's duration — no separate read needed (design D2).
        durationMs = parseResult.chapters.last().endMs,
        chapters = parseResult.chapters,
        chaptersUnreadable = false,
    )

    ChapterParseResult.Unchaptered -> wholeFileBook(title, fileDurationMs, chaptersUnreadable = false)

    is ChapterParseResult.Unreadable -> wholeFileBook(title, fileDurationMs, chaptersUnreadable = true)
}

/**
 * One chapter spanning the whole file, titled with the book (PRD §7.4). A duration of zero means
 * even [MediaMetadataRetriever] could not say how long the file is; the chapter's end is then
 * stored as null, which is the "not yet known" state `BookTimeline` already handles.
 */
private fun wholeFileBook(title: String, fileDurationMs: Long?, chaptersUnreadable: Boolean): M4bContents {
    val durationMs = fileDurationMs ?: 0
    return M4bContents(
        title = title,
        durationMs = durationMs,
        chapters = listOf(Chapter(index = 0, title = title, startMs = 0, endMs = durationMs)),
        chaptersUnreadable = chaptersUnreadable,
    )
}
