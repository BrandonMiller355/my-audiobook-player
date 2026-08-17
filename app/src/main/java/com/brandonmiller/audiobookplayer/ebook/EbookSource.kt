package com.brandonmiller.audiobookplayer.ebook

import android.content.ContentResolver
import android.net.Uri

/**
 * Reads a linked EPUB through the content resolver, turning the ways a document URI can fail into
 * the same [EbookParseResult] the parser already speaks.
 *
 * The failures worth separating are the ones PRD §22 names: a file that has been deleted and a
 * grant that has been revoked both mean "this ebook is no longer available", and neither may reach
 * the user as a crash.
 */
class EbookSource(private val contentResolver: ContentResolver) {

    fun read(uri: Uri): EbookParseResult =
        try {
            contentResolver.openInputStream(uri)?.use { EpubParser.parse(it) }
                ?: EbookParseResult.Unreadable("the ebook could not be opened")
        } catch (e: SecurityException) {
            EbookParseResult.Unreadable("permission to read the ebook was lost")
        } catch (e: Exception) {
            // Broad on purpose: a deleted file, a detached SD card, and a provider that has gone
            // away all surface differently, and none of them may reach the caller as a throw.
            EbookParseResult.Unreadable("the ebook could not be read (${e.javaClass.simpleName})")
        }
}
