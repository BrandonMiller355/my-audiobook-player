package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Builds a [BookTimeline] from [resolvedDurations] — read eagerly via [resolveChapterDurations],
 * independent of playback — falling back to the player's own live [Timeline] for any chapter not
 * yet resolved (belt and braces: if resolution failed for some reason, playback still fills it in
 * naturally as before). Chapter durations are not stored anywhere persistent (folder books never
 * persisted them; see `add-folder-audiobooks` design D5), so both sources are recomputed each time
 * this is needed rather than cached here.
 *
 * Assumes a folder book's shape: one chapter per [androidx.media3.common.MediaItem]. There is
 * nothing to distinguish an `.m4b` book by yet — no book of that shape is wired into the app — so
 * this is the only source [BookTimeline] is built from today. It reduces to the same [ChapterBounds]
 * shape either way, which is the point of [BookTimeline] accepting both.
 */
internal fun Player.chapterTimeline(resolvedDurations: List<Long?> = emptyList()): BookTimeline {
    val timeline = currentTimeline
    val window = Timeline.Window()
    val bounds = (0 until mediaItemCount).map { index ->
        val durationMs = resolvedDurations.getOrNull(index) ?: run {
            timeline.getWindow(index, window)
            if (window.durationUs != C.TIME_UNSET) window.durationUs / 1000 else null
        }
        ChapterBounds(chapterIndex = index, mediaItemIndex = index, startInItemMs = 0, endInItemMs = durationMs)
    }
    // Nothing loaded yet — a single placeholder chapter keeps BookTimeline's non-empty invariant
    // without callers needing to null-check before every operation.
    return BookTimeline(bounds.ifEmpty { listOf(ChapterBounds(0, 0, 0, null)) })
}

internal fun Player.currentLocation(timeline: BookTimeline): Location =
    timeline.locate(currentMediaItemIndex, currentPosition)
