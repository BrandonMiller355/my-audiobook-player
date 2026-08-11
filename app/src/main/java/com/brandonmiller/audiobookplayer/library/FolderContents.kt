package com.brandonmiller.audiobookplayer.library

/** One child of the selected folder, as reported by the document provider. */
data class ScannedEntry(
    val documentId: String,
    val name: String,
    val mimeType: String?,
)

/**
 * Decides which children of a folder are chapters and in what order.
 *
 * Kept free of Android types so the rules that actually matter — which files count, and how they
 * are ordered — are unit-testable without a device.
 */
object FolderContents {

    const val MIME_TYPE_DIRECTORY = "vnd.android.document/directory"

    /**
     * Extensions rather than MIME types decide what counts as audio. Document providers report
     * MIME inconsistently — `application/octet-stream` for a perfectly good mp3 is common from
     * removable storage, which is where this library actually lives.
     */
    val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav")

    fun isSupportedAudio(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Subdirectories are ignored entirely: one selected folder is one book. Recursing would fuse
     * a series folder — 357 files across nine novels, in this library — into a single entry.
     */
    fun audioFilesInOrder(entries: List<ScannedEntry>): List<ScannedEntry> =
        entries
            .filter { it.mimeType != MIME_TYPE_DIRECTORY && isSupportedAudio(it.name) }
            .sortedWith(compareBy(NaturalOrder) { it.name })

    /** A chapter's title is its file name without the extension (PRD §11). */
    fun chapterTitle(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }
}
