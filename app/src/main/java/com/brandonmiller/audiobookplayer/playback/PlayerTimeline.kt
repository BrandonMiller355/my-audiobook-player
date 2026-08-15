package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Builds a [BookTimeline] from whichever source the loaded book has.
 *
 * A single-file book carries its chapter boundaries in the current item's metadata extras, put
 * there by [mediaItemsFor] when the playlist was set (design D1). That branch is checked first
 * because it is exact and available immediately — nothing has to resolve.
 *
 * A folder book has no such extras and is derived per media item, from [resolvedDurations] — read
 * eagerly via [resolveChapterDurations], independent of playback — falling back to the player's own
 * live [Timeline] for any chapter not yet resolved (belt and braces: if resolution failed for some
 * reason, playback still fills it in naturally as before). Folder chapter durations are not stored
 * anywhere persistent (see `add-folder-audiobooks` design D5), so both sources are recomputed each
 * time this is needed rather than cached here.
 *
 * Both shapes reduce to the same [ChapterBounds], which is the point of [BookTimeline] accepting
 * both.
 */
internal fun Player.chapterTimeline(resolvedDurations: List<Long?> = emptyList()): BookTimeline {
    singleFileChapterBounds()?.let { return BookTimeline(it) }

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

/** Null for a folder book, whose items carry no bounds — that is how the two shapes are told apart. */
private fun Player.singleFileChapterBounds(): List<ChapterBounds>? {
    val extras = currentMediaItem?.mediaMetadata?.extras ?: return null
    val starts = extras.getLongArray(EXTRA_CHAPTER_STARTS_MS)?.takeIf { it.isNotEmpty() } ?: return null
    return chapterBoundsFrom(starts, extras.getLong(EXTRA_BOOK_DURATION_MS))
}

/**
 * Every chapter of a single-file book lives at media item 0, its end chained from the next
 * chapter's start. The last chapter's end is the book's duration, or `null` when that is not known
 * — the same "not yet known" state a folder chapter's unresolved duration produces.
 */
internal fun chapterBoundsFrom(startsMs: LongArray, bookDurationMs: Long): List<ChapterBounds> =
    startsMs.mapIndexed { index, startMs ->
        ChapterBounds(
            chapterIndex = index,
            mediaItemIndex = 0,
            startInItemMs = startMs,
            endInItemMs = if (index + 1 < startsMs.size) {
                startsMs[index + 1]
            } else {
                bookDurationMs.takeIf { it > startMs }
            },
        )
    }
