package com.brandonmiller.audiobookplayer.playback

/**
 * One chapter's position within the Media3 playlist.
 *
 * A folder book gives every chapter its own [mediaItemIndex] with [startInItemMs] zero. An `.m4b`
 * book (not yet wired into the app) will share one [mediaItemIndex] across all chapters, with
 * [startInItemMs]/[endInItemMs] marking sub-ranges within that single item. Both shapes reduce to
 * the same four operations below, which is why one type serves both rather than two parallel ones.
 *
 * [endInItemMs] is `null` when the boundary is not yet known — for a folder chapter whose duration
 * ExoPlayer has not resolved yet, or for the book's final chapter.
 */
data class ChapterBounds(
    val chapterIndex: Int,
    val mediaItemIndex: Int,
    val startInItemMs: Long,
    val endInItemMs: Long?,
)

/** Where playback currently is, in chapter terms. */
data class Location(val chapterIndex: Int, val offsetMs: Long)

/** Where playback should move to, in player terms — apply with `player.seekTo(mediaItemIndex, positionMs)`. */
data class PlayerTarget(val mediaItemIndex: Int, val positionMs: Long)

/** One chapter's extent within the book, for a chapter list. [durationMs] is null when unknown. */
data class ChapterSpan(val chapterIndex: Int, val absoluteStartMs: Long, val durationMs: Long?)

/**
 * Maps between Media3 player coordinates and book-chapter coordinates, for either book shape.
 *
 * [bounds] must be sorted by [ChapterBounds.chapterIndex] ascending and cover the whole book with
 * no gaps: each chapter's absolute start is the previous chapter's absolute end.
 */
class BookTimeline(private val bounds: List<ChapterBounds>) {

    init {
        require(bounds.isNotEmpty()) { "a book has at least one chapter" }
    }

    /** Locates [mediaItemIndex]/[positionMs] within the book's chapters. */
    fun locate(mediaItemIndex: Int, positionMs: Long): Location {
        val chapter = bounds.lastOrNull { it.mediaItemIndex == mediaItemIndex && it.startInItemMs <= positionMs }
            ?: bounds.firstOrNull { it.mediaItemIndex == mediaItemIndex }
            ?: bounds.first()
        return Location(chapter.chapterIndex, (positionMs - chapter.startInItemMs).coerceAtLeast(0))
    }

    /**
     * Applies a signed [deltaMs] from [from], clamping at the book's start and end. When the delta
     * crosses into a neighboring chapter whose boundary is known, the target lands there with the
     * correct overflow; when the boundary is not known, it clamps to the current chapter's edge
     * instead of guessing — the fallback PRD §7.3 explicitly allows.
     */
    fun seekTarget(from: Location, deltaMs: Long): PlayerTarget {
        val chapter = boundsFor(from.chapterIndex)
        val absoluteOffset = from.offsetMs + deltaMs

        return when {
            absoluteOffset < 0 -> rollBackward(chapter, -absoluteOffset)
            else -> rollForward(chapter, absoluteOffset)
        }
    }

    /**
     * PRD §7.4's rule, applied uniformly to every book type: restart the current chapter when more
     * than [toleranceMs] into it, otherwise move to the previous chapter. At the first chapter,
     * restarting is the uniform outcome rather than a no-op special case.
     */
    fun previousChapterTarget(from: Location, toleranceMs: Long = 3000): PlayerTarget {
        val chapter = boundsFor(from.chapterIndex)
        return if (from.offsetMs > toleranceMs || chapter.chapterIndex == bounds.first().chapterIndex) {
            PlayerTarget(chapter.mediaItemIndex, chapter.startInItemMs)
        } else {
            val previous = boundsFor(chapter.chapterIndex - 1)
            PlayerTarget(previous.mediaItemIndex, previous.startInItemMs)
        }
    }

    /** The next chapter's start, or `null` at the final chapter — PRD §7.4's "do nothing" case. */
    fun nextChapterTarget(from: Location): PlayerTarget? {
        val nextIndex = from.chapterIndex + 1
        if (nextIndex > bounds.last().chapterIndex) return null
        val next = boundsFor(nextIndex)
        return PlayerTarget(next.mediaItemIndex, next.startInItemMs)
    }

    /**
     * Cumulative duration of every chapter before [location]'s, plus its own offset — the value a
     * book-wide scrubber should show. A chapter whose duration is not yet known contributes zero,
     * so this can transiently undercount and correct itself as more durations resolve (design D4).
     */
    fun absolutePosition(location: Location): Long {
        var sum = 0L
        for (chapter in bounds) {
            if (chapter.chapterIndex == location.chapterIndex) break
            sum += chapterDurationOrZero(chapter)
        }
        return sum + location.offsetMs
    }

    /** Best-effort total book duration — see [absolutePosition]'s note on unresolved chapters. */
    fun totalDurationMs(): Long = bounds.sumOf { chapterDurationOrZero(it) }

    /**
     * Every chapter's place in the book, in book coordinates rather than player ones — what a
     * chapter list needs in order to show durations and to seek to a chapter by name.
     *
     * [ChapterSpan.durationMs] is null for a chapter whose end is not yet known, which the caller
     * must render as "unknown" rather than as zero. [ChapterSpan.absoluteStartMs] treats such a
     * chapter as contributing nothing, the same convention [absolutePosition] uses, so the two
     * agree about where a chapter begins even while durations are still resolving.
     */
    fun chapterSpans(): List<ChapterSpan> {
        var start = 0L
        return bounds.map { chapter ->
            val duration = chapter.endInItemMs?.let { it - chapter.startInItemMs }
            ChapterSpan(chapter.chapterIndex, start, duration).also { start += duration ?: 0 }
        }
    }

    /** How much of [location]'s own chapter is left, or null when that chapter's end is unknown. */
    fun remainingInChapter(location: Location): Long? {
        val chapter = boundsFor(location.chapterIndex)
        val duration = chapter.endInItemMs?.let { it - chapter.startInItemMs } ?: return null
        return (duration - location.offsetMs).coerceAtLeast(0)
    }

    /** Maps an absolute book-wide position back to player coordinates — the scrubber's seek target. */
    fun targetForAbsolute(absoluteMs: Long): PlayerTarget {
        var remaining = absoluteMs.coerceAtLeast(0)
        for (chapter in bounds) {
            val duration = chapter.endInItemMs?.let { it - chapter.startInItemMs }
            if (duration == null || remaining <= duration) {
                return PlayerTarget(chapter.mediaItemIndex, chapter.startInItemMs + remaining)
            }
            remaining -= duration
        }
        val last = bounds.last()
        return PlayerTarget(last.mediaItemIndex, last.endInItemMs ?: last.startInItemMs)
    }

    private fun chapterDurationOrZero(chapter: ChapterBounds): Long =
        (chapter.endInItemMs ?: chapter.startInItemMs) - chapter.startInItemMs

    private fun boundsFor(chapterIndex: Int): ChapterBounds =
        bounds.getOrNull(chapterIndex.coerceIn(bounds.indices)) ?: bounds.first()

    private fun rollForward(chapter: ChapterBounds, offsetIntoChapter: Long): PlayerTarget {
        val end = chapter.endInItemMs
        if (end == null || chapter.startInItemMs + offsetIntoChapter <= end) {
            return PlayerTarget(chapter.mediaItemIndex, chapter.startInItemMs + offsetIntoChapter)
        }
        val overflow = (chapter.startInItemMs + offsetIntoChapter) - end
        val nextIndex = chapter.chapterIndex + 1
        if (nextIndex > bounds.last().chapterIndex) {
            // End of the book: clamp rather than run past it.
            return PlayerTarget(chapter.mediaItemIndex, end)
        }
        return rollForward(boundsFor(nextIndex), overflow)
    }

    /** [deficitMs] is how far before [chapter]'s start the target lies — always strictly positive. */
    private fun rollBackward(chapter: ChapterBounds, deficitMs: Long): PlayerTarget {
        if (chapter.chapterIndex == bounds.first().chapterIndex) {
            // Start of the book: clamp rather than go negative.
            return PlayerTarget(chapter.mediaItemIndex, 0)
        }
        val previous = boundsFor(chapter.chapterIndex - 1)
        val previousDuration = previous.endInItemMs?.let { it - previous.startInItemMs }
        return when {
            previousDuration == null ->
                // Unknown duration behind us: clamp at this chapter's start rather than guess how
                // far back the previous one extends.
                PlayerTarget(chapter.mediaItemIndex, chapter.startInItemMs)
            previousDuration >= deficitMs ->
                PlayerTarget(previous.mediaItemIndex, previous.startInItemMs + (previousDuration - deficitMs))
            else -> rollBackward(previous, deficitMs - previousDuration)
        }
    }
}
