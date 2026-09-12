package com.brandonmiller.audiobookplayer.playback

import kotlin.math.roundToInt

/**
 * One correction the owner entered: at [audioMs] the narrator is at [charOffset] characters into
 * the ebook (`add-readalong-nudge` design D1).
 *
 * The playback-side form of `ReadAlongCorrectionEntity`, kept free of Room so the arithmetic below
 * can be exercised without a database. The reader adapts between the two.
 */
data class ReadAlongCorrection(val audioMs: Long, val charOffset: Int)

/** How far one nudge moves the text, in milliseconds of narration (design D5). */
const val NUDGE_STEP_MS = 10_000L

/**
 * How long a correction takes to unwind, as a multiple of its own size (design D2, option B).
 *
 * The unwinding segment runs at `R · (1 − δ/W)`, so this multiple is what sets how abrupt it is:
 * three gives `2R/3` when the text was pushed later and `4R/3` when it was pulled earlier, both
 * comfortably inside [ReadAlongMap.RATE_GUARD_RATIO] rather than sitting on it. A smaller multiple
 * would hold the correction longer at the cost of a sharper unwind; the minimum that stays in-ratio
 * is two, which lands exactly on the guard and leaves nothing for rounding.
 */
private const val RECONCILE_WINDOW_MULTIPLE = 3

/**
 * Merges the owner's corrections into the chapter-boundary anchors the matcher produced.
 *
 * Each correction contributes up to *two* anchors, which is what makes the correction hold rather
 * than fade (design D2, option B):
 *
 * ```
 *   chars                                    ● chapter end (exact)
 *     |                              ,-'
 *     |    ★────────────────────★'          ← held flat at the full correction …
 *     |   /                       ⋱          … then unwound over the last W
 *     |  /
 *     ● chapter start (exact)
 *     +--------------------------------------> ms
 * ```
 *
 * Shipping one anchor first (option A) put the whole chapter on the taper, and the owner measured it
 * coming back at 3.2 seconds per minute — the correction was gone well before the chapter ended.
 * Holding it flat and confining the unwind to a window at the end keeps it applied for the stretch
 * being listened to.
 *
 * The window cannot be snapped shut at the boundary itself: [ReadAlongMap] requires
 * `absoluteChars` to be non-decreasing alongside `absoluteMs`, and a positive correction collapsing
 * at a boundary would decrease chars against flat ms. It has to unwind *before* the boundary, which
 * is what the window is.
 *
 * There is always room for that window, because `expressibleCorrectionRange` has already bounded the
 * correction to at most half the time remaining in the chapter — the same bound, derived from the
 * same rate limit, so a correction that was accepted can always be unwound in-ratio. When the window
 * would not fit anyway it is clipped to the room available, which degenerates to the single-anchor
 * shape and is still in-ratio for the same reason.
 *
 * Two kinds of stored row are dropped rather than trusted, because a violation of the map's ordering
 * requirement is a corrupted map rather than a mislaid correction:
 *
 * - one whose audio position falls outside the mapped range, or exactly on a chapter boundary,
 *   where there is no segment to bend;
 * - one whose character offset falls outside its own chapter, which is clamped back inside. That
 *   can happen to a row written before the whole-book chapter offset changed under it.
 */
fun anchorsWithCorrections(
    chapterAnchors: List<ReadAlongAnchor>,
    corrections: List<ReadAlongCorrection>,
): List<ReadAlongAnchor> {
    if (corrections.isEmpty() || chapterAnchors.size < 2) return chapterAnchors

    val merged = chapterAnchors.toMutableList()
    for (correction in corrections) {
        val index = chapterAnchors.indexOfLast { it.absoluteMs <= correction.audioMs }
        if (index < 0 || index >= chapterAnchors.size - 1) continue

        val start = chapterAnchors[index]
        val end = chapterAnchors[index + 1]
        if (correction.audioMs <= start.absoluteMs || correction.audioMs >= end.absoluteMs) continue

        val chars = correction.charOffset.coerceIn(start.absoluteChars, end.absoluteChars)
        merged += ReadAlongAnchor(correction.audioMs, chars, ownerEntered = true)

        holdAnchor(start, end, correction.audioMs, chars)?.let { merged += it }
    }
    return merged.sortedBy { it.absoluteMs }
}

/**
 * The second anchor of the pair: where the correction stops being held and starts unwinding.
 *
 * Null when the chapter's own rate cannot be measured, or when the window fills everything left
 * after the correction — in which case the unwind starts immediately and the single anchor already
 * describes it.
 */
private fun holdAnchor(
    start: ReadAlongAnchor,
    end: ReadAlongAnchor,
    correctionMs: Long,
    correctionChars: Int,
): ReadAlongAnchor? {
    val chapterMs = end.absoluteMs - start.absoluteMs
    val chapterChars = end.absoluteChars - start.absoluteChars
    if (chapterMs <= 0 || chapterChars <= 0) return null
    val rate = chapterChars.toDouble() / chapterMs

    // How far the correction moved the text, in milliseconds of this chapter's narration.
    val deltaMs = (correctionChars - start.absoluteChars) / rate - (correctionMs - start.absoluteMs)
    if (deltaMs == 0.0) return null

    val roomAfter = end.absoluteMs - correctionMs
    val window = minOf(RECONCILE_WINDOW_MULTIPLE * kotlin.math.abs(deltaMs), roomAfter.toDouble())
    val holdUntilMs = end.absoluteMs - window.toLong()
    if (holdUntilMs <= correctionMs) return null

    // Still carrying the full correction here, which is what "held flat" means: the text between the
    // two anchors advances at exactly the chapter's own rate.
    val holdChars = start.absoluteChars +
        (rate * (holdUntilMs + deltaMs - start.absoluteMs)).roundToInt()

    return ReadAlongAnchor(
        absoluteMs = holdUntilMs,
        absoluteChars = holdChars.coerceIn(correctionChars, end.absoluteChars),
        ownerEntered = true,
    )
}

/**
 * The correction range the chapter around [anchorMs] can actually express, or null when [anchorMs]
 * is not inside a chapter the map covers.
 *
 * A correction re-slopes both halves of its chapter, and how far it can go is bounded by the room on
 * each side of it. Writing `b` for the milliseconds back to the chapter's start and `a` for the
 * milliseconds on to its end, and `R` for the chapter's own rate:
 *
 * ```
 *   rate before the correction = R · (1 + δ/b)
 *   rate after  the correction = R · (1 − δ/a)
 * ```
 *
 * Requiring both to stay within [ReadAlongMap.RATE_GUARD_RATIO] of `R` gives the range returned
 * here. It collapses toward zero as [anchorMs] approaches either boundary, which is the real
 * constraint rather than a policy choice: 19 seconds before a chapter ends there is only 19 seconds
 * of text left to redistribute, so a correction of a minute has nowhere to go.
 *
 * Without this bound a correction near a boundary produced a segment far off the book's rate, and
 * because such a segment is bounded by an owner anchor the D8 exemption meant nothing contained it —
 * the text raced to the next chapter's opening over the final seconds, which reads as that chapter
 * starting in the wrong place. Bounding the correction here is what makes the D8 exemption safe:
 * a correction that fits in this range cannot produce an out-of-ratio segment to begin with.
 */
fun expressibleCorrectionRange(
    chapterAnchors: List<ReadAlongAnchor>,
    anchorMs: Long,
): LongRange? {
    if (chapterAnchors.size < 2) return null
    val index = chapterAnchors.indexOfLast { it.absoluteMs <= anchorMs }
    if (index < 0 || index >= chapterAnchors.size - 1) return null

    val roomBefore = anchorMs - chapterAnchors[index].absoluteMs
    val roomAfter = chapterAnchors[index + 1].absoluteMs - anchorMs
    if (roomBefore <= 0 || roomAfter <= 0) return null

    val g = ReadAlongMap.RATE_GUARD_RATIO
    val lower = maxOf(-roomBefore / g, -roomAfter * (g - 1.0))
    val upper = minOf(roomBefore * (g - 1.0), roomAfter * (1.0 - 1.0 / g))
    return lower.toLong()..upper.toLong()
}

/**
 * How far a correction has moved the text, in milliseconds of narration — the figure the stepper
 * shows, and the one a further nudge adds to.
 *
 * Read against [base], the uncorrected map, so that it is the exact inverse of
 * [correctionFor]: measuring and re-applying round-trip rather than compounding. Positive means the
 * text is showing what the automatic correspondence would have shown this much later.
 */
fun correctionDeltaMs(base: ReadAlongMap, correction: ReadAlongCorrection): Long =
    base.msForChars(correction.charOffset) - correction.audioMs

/**
 * The correction a nudge produces: at [anchorMs], show the text the uncorrected map would have
 * shown [deltaMs] further on (design D6).
 *
 * [anchorMs] is frozen at the first tap of a burst rather than re-read per tap, because the audio
 * carries on playing while the owner is still tapping and a moving reference would make each tap
 * mean something slightly different.
 */
fun correctionFor(base: ReadAlongMap, anchorMs: Long, deltaMs: Long): ReadAlongCorrection =
    ReadAlongCorrection(audioMs = anchorMs, charOffset = base.charsForMs(anchorMs + deltaMs))
