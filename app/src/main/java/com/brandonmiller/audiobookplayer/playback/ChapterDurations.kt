package com.brandonmiller.audiobookplayer.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Reads each chapter's real duration from its container header, off the live playback timeline.
 *
 * Device testing showed the assumption `chapterTimeline()` originally relied on — that Media3
 * resolves a playlist's durations quickly on its own — was wrong: durations only resolve at
 * roughly the pace of actual playback, so a 128-chapter, 14-hour book's scrubber total would stay
 * drastically understated for nearly the entire time it is being listened to.
 * [android.media.MediaMetadataRetriever] reads a file's duration directly — a stock Android SDK
 * class, not part of Media3 — independent of whether that item has ever played.
 *
 * (Media3 has no public `MetadataRetriever` utility as of 1.11.0 — that was checked directly
 * against the library's classes, not assumed, after the first attempt at this failed to compile.)
 *
 * [onResolved] fires once per chapter as its duration becomes known, rather than waiting for all
 * of them, so the scrubber's total can grow incrementally instead of jumping once at the end.
 * Concurrency is bounded — each call opens the file via SAF, and 128 simultaneous opens against a
 * document provider is not something to do without a limit.
 */
internal suspend fun resolveChapterDurations(
    context: Context,
    mediaItems: List<MediaItem>,
    maxConcurrency: Int = 6,
    onResolved: suspend (index: Int, durationMs: Long?) -> Unit,
) {
    val semaphore = Semaphore(maxConcurrency)
    coroutineScope {
        mediaItems.forEachIndexed { index, item ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    onResolved(index, retrieveDurationMs(context, item))
                }
            }
        }
    }
}

private fun retrieveDurationMs(context: Context, item: MediaItem): Long? {
    val uri = item.localConfiguration?.uri ?: return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } catch (e: Exception) {
        // A moved file, a revoked grant, an unreadable header — report unknown rather than crash
        // the resolution pass for every other chapter (PRD §22).
        null
    } finally {
        retriever.release()
    }
}
