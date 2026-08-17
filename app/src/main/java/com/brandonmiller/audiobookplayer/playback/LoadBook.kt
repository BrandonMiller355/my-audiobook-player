package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity

/**
 * Puts a book into the session: the playlist, the saved position, and the speed to play it at.
 *
 * Extracted because two screens now need it. The Player has always loaded the book it was opened
 * for, and the redesign's library resume card starts a book without navigating to the Player at
 * all (design D5) — two independently maintained copies of this sequence is how the two screens
 * end up disagreeing about whether opening a book should start it.
 *
 * Returns the media items it set, or null when the controller was already holding this book and
 * nothing was done. Callers use that to decide whether durations need resolving.
 */
internal fun MediaController.loadBook(
    book: AudiobookEntity,
    chapters: List<ChapterEntity>,
    fallbackSpeed: Float,
): LoadedBook? {
    // Reopening a book that is already loaded must not restart it from zero, so only set the
    // playlist when the controller is holding something else.
    if (mediaIdBelongsTo(currentMediaItem?.mediaId, book.id)) return null

    // Cleared first. Switching books while another one is playing would otherwise inherit its
    // "playing" state and start the new book by itself, which is not what opening a book is
    // supposed to do (PRD §6 ends its flow with the user pressing play).
    playWhenReady = false

    val mediaItems = mediaItemsFor(book, chapters)
    setMediaItems(mediaItems)
    prepare()

    val savedIndex = book.lastMediaItemIndex
    val savedPosition = book.lastPositionMs
    if (savedIndex != null && savedPosition != null && savedIndex < mediaItems.size) {
        seekTo(savedIndex, savedPosition)
    }

    val speed = book.playbackSpeed ?: fallbackSpeed
    playbackParameters = PlaybackParameters(speed, 1.0f)

    return LoadedBook(mediaItems = mediaItems, speed = speed)
}

internal data class LoadedBook(val mediaItems: List<MediaItem>, val speed: Float)
