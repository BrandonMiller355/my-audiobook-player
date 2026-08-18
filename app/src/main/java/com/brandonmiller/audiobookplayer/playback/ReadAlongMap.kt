package com.brandonmiller.audiobookplayer.playback

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One point where the audio's absolute position and the book's absolute character position are
 * known to correspond exactly — a chapter boundary in this change (`add-readalong-scroll` design
 * D2), and potentially something finer in a later one without anything below needing to change.
 */
data class ReadAlongAnchor(val absoluteMs: Long, val absoluteChars: Int)

/**
 * The piecewise-linear correspondence between a place in the audio and a place in the ebook's text
 * (design D1). Both directions are the same binary search and the same linear interpolation run
 * backward, so a bug in one direction is a bug in both — the two can never drift into disagreeing
 * about where a given place in the book is.
 *
 * [anchors] must be sorted ascending by [ReadAlongAnchor.absoluteMs], with [ReadAlongAnchor.absoluteChars]
 * non-decreasing alongside it, since both traverse the book in the same direction.
 */
class ReadAlongMap(private val anchors: List<ReadAlongAnchor>) {

    init {
        require(anchors.isNotEmpty()) { "a read-along map needs at least one anchor" }
    }

    /**
     * The book's typical narration rate, in characters per millisecond, across every segment. The
     * baseline [D13's rate guard][isGuarded] measures every segment against.
     */
    private val medianCharsPerMs: Double = segmentRates().median()

    /** The book position, in characters, that corresponds to [ms] of audio. Clamps past either end. */
    fun charsForMs(ms: Long): Int {
        if (ms <= anchors.first().absoluteMs) return anchors.first().absoluteChars
        if (ms >= anchors.last().absoluteMs) return anchors.last().absoluteChars

        val from = anchors[segmentStartForMs(ms)]
        val to = anchors[segmentStartForMs(ms) + 1]
        if (to.absoluteMs == from.absoluteMs) return to.absoluteChars

        val rate = segmentRate(from, to)
        return if (isGuarded(rate)) {
            val advanced = (medianCharsPerMs * (ms - from.absoluteMs)).roundToInt()
            (from.absoluteChars + advanced).coerceIn(from.absoluteChars, to.absoluteChars)
        } else {
            val t = (ms - from.absoluteMs).toDouble() / (to.absoluteMs - from.absoluteMs)
            from.absoluteChars + (t * (to.absoluteChars - from.absoluteChars)).roundToInt()
        }
    }

    /**
     * The audio position, in milliseconds, that corresponds to [chars] of book text — the reverse of
     * [charsForMs]. Clamps past either end.
     */
    fun msForChars(chars: Int): Long {
        if (chars <= anchors.first().absoluteChars) return anchors.first().absoluteMs
        if (chars >= anchors.last().absoluteChars) return anchors.last().absoluteMs

        val from = anchors[segmentStartForChars(chars)]
        val to = anchors[segmentStartForChars(chars) + 1]
        if (to.absoluteChars == from.absoluteChars) return to.absoluteMs

        val rate = segmentRate(from, to)
        return if (isGuarded(rate)) {
            // The segment's real char span outruns what the guarded rate reaches in its duration —
            // that excess is the non-narrated tail (spike finding 5). A target inside it has no
            // audio to seek to, so it clamps at the segment's end rather than extrapolating past it.
            if (medianCharsPerMs <= 0.0) return from.absoluteMs
            val reachable = from.absoluteChars + (medianCharsPerMs * (to.absoluteMs - from.absoluteMs)).roundToInt()
            if (chars > reachable) {
                to.absoluteMs
            } else {
                val t = (chars - from.absoluteChars) / medianCharsPerMs
                (from.absoluteMs + t.roundToLong()).coerceIn(from.absoluteMs, to.absoluteMs)
            }
        } else {
            val t = (chars - from.absoluteChars).toDouble() / (to.absoluteChars - from.absoluteChars)
            from.absoluteMs + (t * (to.absoluteMs - from.absoluteMs)).roundToLong()
        }
    }

    /**
     * A segment's implied rate is wildly out of line with the book's median when it differs by more
     * than [RATE_GUARD_RATIO], the trigger for D13's guard. The epilogue in the spike findings is
     * roughly 5x the median; this catches that with room to spare before ordinary chapter-to-chapter
     * variation (measured p10–p90 spread of 14.6%) would ever trip it.
     */
    private fun isGuarded(rate: Double): Boolean {
        if (!rate.isFinite() || medianCharsPerMs <= 0.0) return false
        val ratio = rate / medianCharsPerMs
        return ratio > RATE_GUARD_RATIO || ratio < 1.0 / RATE_GUARD_RATIO
    }

    private fun segmentRates(): List<Double> =
        (0 until anchors.size - 1).map { segmentRate(anchors[it], anchors[it + 1]) }.filter { it.isFinite() }

    private fun segmentRate(from: ReadAlongAnchor, to: ReadAlongAnchor): Double {
        val duration = to.absoluteMs - from.absoluteMs
        if (duration <= 0) return Double.POSITIVE_INFINITY
        return (to.absoluteChars - from.absoluteChars).toDouble() / duration
    }

    private fun segmentStartForMs(ms: Long): Int {
        val found = anchors.binarySearchBy(ms) { it.absoluteMs }
        val index = if (found >= 0) found else -found - 2
        return index.coerceIn(0, anchors.size - 2)
    }

    private fun segmentStartForChars(chars: Int): Int {
        val found = anchors.binarySearchBy(chars) { it.absoluteChars }
        val index = if (found >= 0) found else -found - 2
        return index.coerceIn(0, anchors.size - 2)
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private companion object {
        const val RATE_GUARD_RATIO = 2.0
    }
}
