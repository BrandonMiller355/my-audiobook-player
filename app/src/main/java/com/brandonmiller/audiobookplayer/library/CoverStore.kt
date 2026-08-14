package com.brandonmiller.audiobookplayer.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Caches a book's cover as a small JPEG in app-private storage, one file per book.
 *
 * `filesDir` rather than `cacheDir` on purpose (design D7): the system may evict `cacheDir` at any
 * time, and a cover would then vanish with no way to notice or rebuild it short of reparsing a
 * multi-gigabyte container. The files are small and are deleted with the book.
 *
 * The user's own files are only ever read. A downsampled thumbnail in private storage is a cache,
 * not the duplicate of their artwork that PRD §10 forbids.
 */
class CoverStore(private val filesDir: File) {

    /** The cached cover's path, or null if there was nothing decodable to cache. */
    fun write(bookId: Long, imageBytes: ByteArray): String? {
        val bitmap = decodeDownsampled(imageBytes) ?: return null
        return try {
            val directory = File(filesDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, "$bookId.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            file.absolutePath
        } catch (e: Exception) {
            // A book without a cover is a book that shows the placeholder, not a failed add.
            Log.w(TAG, "could not cache a cover for book $bookId", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Room's cascade covers rows, not files, so removal has to come through here too. */
    fun delete(bookId: Long) {
        runCatching { File(File(filesDir, DIRECTORY), "$bookId.jpg").delete() }
    }

    /**
     * Bounds first, then a decode at the nearest power-of-two reduction — the point being never to
     * hold a full-resolution cover in memory, which for a real `.m4b` runs to several megapixels.
     * [inSampleSizeFor] lands within a factor of two of the target, so anything still over it is
     * scaled the rest of the way.
     */
    private fun decodeDownsampled(imageBytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)?.let(::scaleWithinBounds)
        }
    } catch (e: Exception) {
        // Artwork that is present but not decodable as an image is not an error — the book is
        // added and treated as having no cover.
        Log.w(TAG, "embedded artwork could not be decoded", e)
        null
    }

    private fun scaleWithinBounds(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_EDGE_PX) return bitmap

        val scale = MAX_EDGE_PX.toFloat() / longestEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            /* filter = */ true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private companion object {
        const val TAG = "CoverStore"
        const val DIRECTORY = "covers"
        const val QUALITY = 85
    }
}

/** Sized for display, not for the source's resolution: a library row and the Player both fit here. */
private const val MAX_EDGE_PX = 512

private fun inSampleSizeFor(width: Int, height: Int): Int {
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > MAX_EDGE_PX) sampleSize *= 2
    return sampleSize
}
