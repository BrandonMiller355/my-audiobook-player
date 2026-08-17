package com.brandonmiller.audiobookplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The figures both screens show. These are pure functions over a millisecond count, so they get
 * plain JUnit — no Android, no Robolectric.
 *
 * The cases that matter are the boundaries: an hour, a minute, and zero, each of which changes the
 * shape of the output rather than just its digits.
 */
class FormatTest {

    @Test
    fun `a timestamp under an hour omits the hour field`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:07", formatTime(7_000))
        assertEquals("12:34", formatTime(12 * 60_000 + 34_000))
        assertEquals("59:59", formatTime(59 * 60_000 + 59_000))
    }

    @Test
    fun `a timestamp of an hour or more zero-pads minutes and seconds`() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("5:12:40", formatTime(5 * 3_600_000 + 12 * 60_000 + 40_000))
        assertEquals("53:20:00", formatTime(53 * 3_600_000 + 20 * 60_000))
    }

    @Test
    fun `a negative position reads as zero rather than going backwards`() {
        // Reachable transiently: the scrubber's total grows as durations resolve, so a remaining
        // figure can be computed against a total that is briefly smaller than the position.
        assertEquals("0:00", formatTime(-5_000))
        assertEquals("0m", formatDuration(-5_000))
    }

    @Test
    fun `a duration reads as hours and minutes`() {
        assertEquals("8h 40m", formatDuration(8 * 3_600_000 + 40 * 60_000))
        assertEquals("53h 20m", formatDuration(53 * 3_600_000 + 20 * 60_000))
        // Zero-padded, so a column of durations stays aligned in a monospaced face.
        assertEquals("24h 05m", formatDuration(24 * 3_600_000 + 5 * 60_000))
    }

    @Test
    fun `a duration under an hour omits the hour field`() {
        assertEquals("40m", formatDuration(40 * 60_000))
        assertEquals("0m", formatDuration(0))
        assertEquals("0m", formatDuration(59_000))
    }

    @Test
    fun `a duration rounds down rather than to nearest`() {
        // A book that runs out before its stated length reads as the app being wrong; one that runs
        // a minute longer than stated is unremarkable.
        assertEquals("1h 00m", formatDuration(3_600_000 + 59_000))
        assertEquals("8h 40m", formatDuration(8 * 3_600_000 + 40 * 60_000 + 59_999))
    }

    @Test
    fun `a chapter length stays in minutes and seconds until it passes an hour`() {
        assertEquals("32:10", formatChapterLength(32 * 60_000 + 10_000))
        assertEquals("0:45", formatChapterLength(45_000))
        assertEquals("1:00:00", formatChapterLength(3_600_000))
    }

    @Test
    fun `every speed stop keeps at least one decimal place`() {
        // The chips are a mono row read at a glance: "1" beside "1.25" is both narrower than its
        // neighbors and briefly ambiguous.
        val expected = listOf(
            0.75f to "0.75",
            0.9f to "0.9",
            1.0f to "1.0",
            1.1f to "1.1",
            1.2f to "1.2",
            1.25f to "1.25",
            1.3f to "1.3",
            1.4f to "1.4",
            1.5f to "1.5",
            1.75f to "1.75",
            2.0f to "2.0",
            2.25f to "2.25",
            2.5f to "2.5",
            3.0f to "3.0",
        )
        expected.forEach { (speed, label) -> assertEquals(label, formatSpeed(speed)) }
    }
}
