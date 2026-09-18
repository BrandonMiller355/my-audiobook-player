package com.brandonmiller.audiobookplayer.playback

/**
 * Deciding whether playback has just carried the listener out of a chapter (`add-chapter-summaries`
 * design D6).
 *
 * Pure, and expressed over samples rather than over a player, because the interesting cases are all
 * sequences: a scrub across a boundary, a chapter-skip, the first sample after a resume. None of
 * those needs a device to describe, and none of them is reachable from a test that has to drive
 * Media3 to produce it.
 */

/** One reading of where the book is, taken from the Player's existing position ticker. */
data class PlaybackSample(
    val bookId: Long,
    val chapterIndex: Int,
    /** Position across the whole book, the same figure the scrubber shows. */
    val absolutePositionMs: Long,
)

/**
 * A forward move larger than this did not come from listening.
 *
 * The ticker runs every 500ms, so an uninterrupted crossing shows a delta of about that, stretched by
 * the playback speed — 1.5s at the fastest stop this app offers, or 3s if a tick is missed under
 * load. Five seconds clears all of that comfortably.
 *
 * The ceiling is set by what it has to exclude rather than by what it admits: the smallest deliberate
 * jump in the app is the 10-second seek button, and both chapter-skip and a scrub move much further
 * still. Anything between 3 and 10 seconds would work; five is in the middle of that gap.
 */
private const val MAX_CROSSING_ADVANCE_MS = 5_000L

/**
 * The chapter that playback just finished, or null if nothing finished.
 *
 * Returns the index of the chapter that *ended* — the one to offer a summary for — rather than the
 * one now playing.
 *
 * Four conditions, each ruling out a different way the chapter index can change without the narration
 * having carried anyone anywhere:
 *
 * - **Playing.** A chapter list tapped while paused moves the index with nothing being listened to.
 * - **A previous sample, for the same book.** Resuming a book lands mid-chapter with no predecessor,
 *   and opening a different book is not a crossing of anything.
 * - **The index advanced by exactly one.** Rules out every backward move, and every jump across
 *   several chapters.
 * - **The position advanced by no more than [MAX_CROSSING_ADVANCE_MS].** This is the one that does
 *   the real work. A seek, a scrub, and a chapter-skip all move the book-wide position by far more
 *   than one tick's worth, so none of them needs to be recognized as *itself* — the size of the move
 *   says on its own that narration did not cover the distance.
 */
fun chapterFinishedBy(previous: PlaybackSample?, current: PlaybackSample, isPlaying: Boolean): Int? {
    if (!isPlaying) return null
    if (previous == null || previous.bookId != current.bookId) return null
    if (current.chapterIndex != previous.chapterIndex + 1) return null

    val advance = current.absolutePositionMs - previous.absolutePositionMs
    if (advance < 0 || advance > MAX_CROSSING_ADVANCE_MS) return null

    return previous.chapterIndex
}
