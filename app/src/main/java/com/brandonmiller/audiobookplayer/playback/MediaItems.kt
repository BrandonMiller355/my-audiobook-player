package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity

/**
 * A folder book becomes an ordered playlist, one item per chapter file, so ExoPlayer advances
 * between chapters on its own and `seekToNext`/`seekToPrevious` — including from a Bluetooth
 * remote — already mean chapter navigation (PRD §14).
 *
 * The media id encodes which book an item belongs to, so the Player can tell whether the
 * controller is already holding this book and avoid resetting the playlist underneath it.
 */
internal fun mediaItemsFor(book: AudiobookEntity, chapters: List<ChapterEntity>): List<MediaItem> =
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

internal fun mediaIdFor(bookId: Long, chapterIndex: Int): String = "$bookId:$chapterIndex"

internal fun mediaIdBelongsTo(mediaId: String?, bookId: Long): Boolean =
    mediaId != null && mediaId.substringBefore(':') == bookId.toString()
