package com.brandonmiller.audiobookplayer.ui

import java.util.Locale

/**
 * The numbers both screens show, formatted in one place.
 *
 * These moved out of `PlayerScreen` when the redesign gave the library a duration on every row and
 * a remaining time on its resume card: two independently maintained ways to render `8h 40m` is how
 * the same book ends up reading differently on two screens a swipe apart.
 *
 * [Locale.US] throughout, deliberately. These are clock readings composed of digits and fixed
 * separators, not prose; a locale-dependent decimal or digit shape would break the alignment the
 * design's monospaced numerals exist to provide.
 */

/** `h:mm:ss` for anything an hour or longer, `m:ss` below that. The scrubber's form. */
fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * `8h 40m` for a book's length, `40m` when it is under an hour, `0m` when it is under a minute.
 *
 * Rounded down rather than to nearest: a book listed as `8h 40m` that turns out to be eight hours
 * and forty-one minutes is unremarkable, whereas one that runs out before its stated length reads
 * as the app being wrong.
 */
fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, minutes)
    } else {
        String.format(Locale.US, "%dm", minutes)
    }
}

/**
 * `mm:ss` — a chapter's own length in the sheet, where every row is minutes rather than hours and
 * a leading `0:` on each would be noise.
 */
fun formatChapterLength(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        formatTime(millis)
    } else {
        String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

/**
 * A speed's label without a unit: `0.75`, `1.0`, `1.25`, `2.0`. The `×` is the caller's, because
 * the sheet's chips carry none and the Player's footer does.
 *
 * Always at least one decimal place, which is a change from the version this replaced — that one
 * rendered 1.0x as `1`. The stops are a row of mono chips read at a glance, and `1` beside `1.25`
 * is both narrower than its neighbors and briefly ambiguous; `1.0` lines up and does not need a
 * second look. Trailing precision beyond the first decimal is still dropped, so `0.90` reads
 * `0.9`.
 */
fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.US, "%.2f", speed)
    return if (text.endsWith("0")) text.dropLast(1) else text
}
