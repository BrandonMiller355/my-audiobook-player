package com.brandonmiller.audiobookplayer.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B

/**
 * Absolute, ascending chapter start times within a single-file book, with `starts[0] == 0`.
 *
 * Ends are chained from the next start rather than carried in a second array, so the no-gaps
 * invariant [BookTimeline] requires is enforced by construction instead of by trusting two arrays
 * to agree (design D1).
 */
internal const val EXTRA_CHAPTER_STARTS_MS = "com.brandonmiller.audiobookplayer.CHAPTER_STARTS_MS"

/** The single-file book's full duration — the last chapter's end, which has no next start. */
internal const val EXTRA_BOOK_DURATION_MS = "com.brandonmiller.audiobookplayer.BOOK_DURATION_MS"

/**
 * A folder book becomes an ordered playlist, one item per chapter file, so ExoPlayer advances
 * between chapters on its own and `seekToNext`/`seekToPrevious` — including from a Bluetooth
 * remote — already mean chapter navigation (PRD §14).
 *
 * An `.m4b` book becomes one item for the whole file, and its chapter boundaries travel inside that
 * item's metadata extras (design D1). [ChapterAwarePlayer] has to answer "where is the previous
 * chapter?" synchronously, with no Activity alive and no playlist shape to read; attaching the
 * bounds to the item means whoever sets the playlist supplies them, so every control surface reads
 * one source and they cannot disagree.
 *
 * The media id encodes which book an item belongs to, so the Player can tell whether the
 * controller is already holding this book and avoid resetting the playlist underneath it.
 */
internal fun mediaItemsFor(book: AudiobookEntity, chapters: List<ChapterEntity>): List<MediaItem> =
    if (book.sourceType == SOURCE_TYPE_M4B && chapters.isNotEmpty()) {
        listOf(singleFileItem(book, chapters))
    } else {
        chapters.map { chapter ->
            MediaItem.Builder()
                .setUri(chapter.mediaUri)
                .setMediaId(mediaIdFor(book.id, chapter.chapterIndex))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setArtist(book.title)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build(),
                )
                .build()
        }
    }

/**
 * The title here names the book, not the chapter — a single-item book has one title to give, and
 * it is the notification and lock screen that read it. The Player takes chapter identity from the
 * stored chapter list instead (design D6).
 */
private fun singleFileItem(book: AudiobookEntity, chapters: List<ChapterEntity>): MediaItem {
    val bounds = Bundle().apply {
        putLongArray(EXTRA_CHAPTER_STARTS_MS, chapters.map { it.startPositionMs }.toLongArray())
        // Zero when even MediaMetadataRetriever could not say how long the file is; the timeline
        // reads that as "the last chapter's end is not known", which BookTimeline already handles.
        putLong(EXTRA_BOOK_DURATION_MS, chapters.last().endPositionMs ?: 0)
    }

    return MediaItem.Builder()
        .setUri(chapters.first().mediaUri)
        .setMediaId(mediaIdFor(book.id, 0))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(book.title)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(bounds)
                .build(),
        )
        .build()
}

internal fun mediaIdFor(bookId: Long, chapterIndex: Int): String = "$bookId:$chapterIndex"

internal fun mediaIdBelongsTo(mediaId: String?, bookId: Long): Boolean =
    mediaId != null && mediaId.substringBefore(':') == bookId.toString()

/** The book a media id belongs to, regardless of which book that is — used by the service, which
 * does not otherwise track "which book is currently loaded" (design D6). */
internal fun mediaIdBookId(mediaId: String?): Long? = mediaId?.substringBefore(':')?.toLongOrNull()
