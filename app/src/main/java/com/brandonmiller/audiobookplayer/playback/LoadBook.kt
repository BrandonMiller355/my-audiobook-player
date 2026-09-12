package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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
 *
 * Takes a [Player] rather than a `MediaController` because that is all it uses, and because a
 * `MediaController` cannot be constructed in a test — the ordering this function depends on is
 * exactly the kind that needs pinning (see `LoadBookTest`).
 */
internal fun Player.loadBook(
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

    // The playlist and the saved position go in together, rather than setting the playlist and
    // seeking to the position afterwards. Setting them separately loses the saved position
    // outright: `setMediaItems` fires `onMediaItemTransition`, `PlaybackService` writes progress
    // from that callback, and its scope is `Dispatchers.Main.immediate` — so the write reads
    // `currentPosition` inline, before the seek two lines below has happened, and stores zero over
    // the position being restored. The player then seeks and looks correct, which is what made this
    // hard to see: the screen shows the right place while the database holds zero. Cold-open a book
    // and leave without playing, and the book has lost its place.
    val savedIndex = book.lastMediaItemIndex
    val savedPosition = book.lastPositionMs
    if (savedIndex != null && savedPosition != null && savedIndex < mediaItems.size) {
        setMediaItems(mediaItems, savedIndex, savedPosition)
    } else {
        setMediaItems(mediaItems)
    }
    prepare()

    val speed = book.playbackSpeed ?: fallbackSpeed
    playbackParameters = PlaybackParameters(speed, 1.0f)

    return LoadedBook(mediaItems = mediaItems, speed = speed)
}

internal data class LoadedBook(val mediaItems: List<MediaItem>, val speed: Float)
