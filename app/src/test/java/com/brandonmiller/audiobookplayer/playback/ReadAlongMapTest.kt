package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAlongMapTest {

    // Three chapters, each 100_000ms and 1_000 chars, so the median rate is a round 0.01 chars/ms.
    private val regular = listOf(
        ReadAlongAnchor(0, 0),
        ReadAlongAnchor(100_000, 1_000),
        ReadAlongAnchor(200_000, 2_000),
        ReadAlongAnchor(300_000, 3_000),
    )

    // ------------------------------------------------------------------ round trip

    @Test
    fun `every anchor maps exactly in both directions`() {
        val map = ReadAlongMap(regular)

        for (anchor in regular) {
            assertEquals(anchor.absoluteChars, map.charsForMs(anchor.absoluteMs))
            assertEquals(anchor.absoluteMs, map.msForChars(anchor.absoluteChars))
        }
    }

    @Test
    fun `midpoints interpolate linearly`() {
        val map = ReadAlongMap(regular)

        assertEquals(500, map.charsForMs(50_000))
        assertEquals(50_000L, map.msForChars(500))
        assertEquals(1_500, map.charsForMs(150_000))
        assertEquals(150_000L, map.msForChars(1_500))
    }

    @Test
    fun `positions before the first anchor clamp rather than extrapolate`() {
        val map = ReadAlongMap(regular)

        assertEquals(0, map.charsForMs(-5_000))
        assertEquals(0L, map.msForChars(-5))
    }

    @Test
    fun `positions after the last anchor clamp rather than extrapolate`() {
        val map = ReadAlongMap(regular)

        assertEquals(3_000, map.charsForMs(999_000))
        assertEquals(300_000L, map.msForChars(9_000))
    }

    @Test
    fun `a value mapped forward and back returns to itself`() {
        val map = ReadAlongMap(regular)

        for (ms in listOf(0L, 25_000L, 100_000L, 175_000L, 300_000L)) {
            assertEquals(ms, map.msForChars(map.charsForMs(ms)))
        }
    }

    // ------------------------------------------------------------------ D13 rate guard

    @Test
    fun `the epilogue's non-narrated tail does not race the text past what was narrated`() {
        // Ten normal chapters at the book's actual measured rate, 13.4 chars/sec, each 5 minutes.
        val chapterMs = 300_000L
        val normalChars = (13.4 * chapterMs / 1000).toInt()
        val normal = (0..10).map { i -> ReadAlongAnchor(i * chapterMs, i * normalChars) }

        // The epilogue: spike finding 5's shape — a 2.2-minute segment carrying about 7,000 extra,
        // non-narrated characters on top of what 13.4 chars/sec would predict.
        val epilogueMs = 132_000L
        val narratedChars = (13.4 * epilogueMs / 1000).toInt()
        val epilogueEnd = ReadAlongAnchor(
            normal.last().absoluteMs + epilogueMs,
            normal.last().absoluteChars + narratedChars + 7_000,
        )
        val anchors = normal + epilogueEnd
        val map = ReadAlongMap(anchors)

        val epilogueStart = normal.last()
        val atEnd = map.charsForMs(epilogueEnd.absoluteMs - 1)

        // Tracks near the narrated pace rather than the segment's inflated implied rate...
        val expectedNarrated = epilogueStart.absoluteChars + narratedChars
        assertTrue(
            "expected roughly $expectedNarrated narrated chars, got $atEnd",
            atEnd in (expectedNarrated - 200)..(expectedNarrated + 200),
        )
        // ...and stops well short of the segment's actual (inflated) endpoint.
        assertTrue(atEnd < epilogueEnd.absoluteChars - 5_000)
    }

    @Test
    fun `seeking into the epilogue's non-narrated tail clamps at the segment's end`() {
        // Nine normal 5-minute/1340-char segments, then a guarded one shaped like the epilogue.
        val chapterMs = 300_000L
        val normalChars = 1_340
        val normal = (0..9).map { i -> ReadAlongAnchor(i * chapterMs, i * normalChars) }
        val epilogue = ReadAlongAnchor(normal.last().absoluteMs + 132_000, normal.last().absoluteChars + 8_768)
        val map = ReadAlongMap(normal + epilogue)

        // A target deep in the non-narrated tail has no corresponding audio; it clamps at the
        // chapter's end rather than extrapolating into material that was never narrated.
        val target = epilogue.absoluteChars - 100
        assertEquals(epilogue.absoluteMs, map.msForChars(target))
    }

    // ------------------------------------------------------------------ degenerate shapes

    @Test
    fun `a single anchor maps everything to that one point`() {
        val map = ReadAlongMap(listOf(ReadAlongAnchor(50_000, 800)))

        assertEquals(800, map.charsForMs(0))
        assertEquals(800, map.charsForMs(999_999))
        assertEquals(50_000L, map.msForChars(0))
        assertEquals(50_000L, map.msForChars(999_999))
    }

    @Test
    fun `two anchors interpolate as a single segment`() {
        val map = ReadAlongMap(listOf(ReadAlongAnchor(0, 0), ReadAlongAnchor(10_000, 100)))

        assertEquals(50, map.charsForMs(5_000))
        assertEquals(5_000L, map.msForChars(50))
    }

    @Test
    fun `a zero-length segment between two anchors does not divide by zero`() {
        val anchors = listOf(
            ReadAlongAnchor(0, 0),
            ReadAlongAnchor(10_000, 100), // duration collapses to zero here...
            ReadAlongAnchor(10_000, 150), // ...and chars collapses to zero here.
            ReadAlongAnchor(20_000, 250),
        )
        val map = ReadAlongMap(anchors)

        assertEquals(150, map.charsForMs(10_000))
        assertEquals(10_000L, map.msForChars(120))
        assertEquals(10_000L, map.msForChars(150))
    }

    // ------------------------------------------------------------------ the glide's target

    @Test
    fun `the exact position keeps what rounding to a whole character throws away`() {
        val map = ReadAlongMap(regular)

        // The regular book runs at 0.01 chars/ms, so these land 30% and 70% into character 500.
        assertEquals(500.3, map.charsForMsExact(50_030), 1e-9)
        assertEquals(500, map.charsForMs(50_030))

        assertEquals(500.7, map.charsForMsExact(50_070), 1e-9)
        assertEquals(501, map.charsForMs(50_070))
    }

    @Test
    fun `the exact position advances on every frame at reading speed`() {
        // The regular book runs at 0.01 chars/ms — 10 characters a second, a slow but ordinary
        // narration pace. At 60fps that is one character every six frames, so a target rounded to
        // whole characters holds still for five of them. The glide follows this per frame, and a
        // target that only moves once every six is what a stutter looks like on screen.
        val map = ReadAlongMap(regular)
        val frameMs = 1000L / 60

        var previous = map.charsForMsExact(50_000)
        for (frame in 1..120) {
            val current = map.charsForMsExact(50_000 + frame * frameMs)
            assertTrue("frame $frame did not advance: $previous then $current", current > previous)
            previous = current
        }
    }

    @Test
    fun `the exact position never runs backward as time moves forward`() {
        // Spans both directions of the D13 guard and both clamps, since a segment that switches
        // interpolation strategy mid-book is the one place a monotonic map could break.
        val normal = (0..9).map { i -> ReadAlongAnchor(i * 300_000L, i * 1_340) }
        val guarded = ReadAlongAnchor(normal.last().absoluteMs + 132_000, normal.last().absoluteChars + 8_768)
        val map = ReadAlongMap(normal + guarded)

        var previous = map.charsForMsExact(-10_000)
        for (ms in -10_000L..3_100_000L step 250) {
            val current = map.charsForMsExact(ms)
            assertTrue("went backward at ${ms}ms: $previous then $current", current >= previous)
            previous = current
        }
    }
}
