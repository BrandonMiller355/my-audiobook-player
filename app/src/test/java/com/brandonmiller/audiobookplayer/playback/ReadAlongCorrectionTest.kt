package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nudge arithmetic (`add-readalong-nudge` design D6, D7).
 *
 * The properties worth pinning are the ones a reader would notice as the control misbehaving:
 * repeated taps accumulate, opposite taps cancel, a second adjustment refines the first instead of
 * restarting from the automatic correspondence, and no stored row can produce an anchor list that
 * breaks [ReadAlongMap]'s requirement that both coordinates climb.
 */
class ReadAlongCorrectionTest {

    // Five chapters, each 100_000ms and 1_000 chars — a round 0.01 chars/ms throughout.
    private val chapters = (0..4).map { i -> ReadAlongAnchor(i * 100_000L, i * 1_000) }
    private val base = ReadAlongMap(chapters)

    // Mid-way through the third chapter, which runs 200_000..300_000.
    private val anchorMs = 250_000L

    // ------------------------------------------------------------------ measuring and re-applying

    @Test
    fun `a correction measures back to the delta it was made with`() {
        for (delta in longArrayOf(-30_000, -10_000, 10_000, 20_000, 40_000)) {
            val correction = correctionFor(base, anchorMs, delta)
            assertEquals(
                "delta ${delta}ms did not round-trip",
                delta,
                correctionDeltaMs(base, correction),
            )
        }
    }

    @Test
    fun `no correction is no delta`() {
        val correction = correctionFor(base, anchorMs, 0)
        assertEquals(0, correctionDeltaMs(base, correction))
    }

    // ------------------------------------------------------------------ bursts

    @Test
    fun `repeated steps in one direction accumulate`() {
        var delta = 0L
        repeat(3) { delta += NUDGE_STEP_MS }
        val correction = correctionFor(base, anchorMs, delta)

        assertEquals(3 * NUDGE_STEP_MS, correctionDeltaMs(base, correction))
    }

    @Test
    fun `stepping back cancels stepping forward`() {
        val forward = correctionFor(base, anchorMs, 2 * NUDGE_STEP_MS)
        val delta = correctionDeltaMs(base, forward) - 2 * NUDGE_STEP_MS
        val cancelled = correctionFor(base, anchorMs, delta)

        assertEquals(0, correctionDeltaMs(base, cancelled))
        assertEquals(base.charsForMs(anchorMs), cancelled.charOffset)
    }

    /**
     * Design D6's refinement claim, and the reason the delta is measured against the uncorrected map
     * rather than the corrected one. Measuring against the corrected map would compound: the second
     * burst would read its own first correction as if it were the automatic correspondence.
     */
    @Test
    fun `a later burst refines the earlier correction rather than restarting`() {
        val first = correctionFor(base, anchorMs, NUDGE_STEP_MS)

        // A second burst, later in the same chapter, taking the stored correction as its starting point.
        val laterAnchorMs = 260_000L
        val carried = correctionDeltaMs(base, first)
        val second = correctionFor(base, laterAnchorMs, carried + NUDGE_STEP_MS)

        assertEquals(2 * NUDGE_STEP_MS, correctionDeltaMs(base, second))
    }

    // ------------------------------------------------------------------ what a chapter can express

    /**
     * The bound that makes the D8 guard exemption safe. Without it a correction near a boundary
     * produced a segment far off the book's rate, and because that segment is bounded by an owner
     * anchor nothing contained it — the text sprinted to the next chapter's opening over the final
     * seconds, which reads as that chapter starting in the wrong place.
     */
    @Test
    fun `a correction has room in the middle of a chapter`() {
        // Chapter three runs 200_000..300_000, so the midpoint has 50s either side.
        val room = expressibleCorrectionRange(chapters, 250_000)!!

        assertEquals(-25_000, room.first)
        assertEquals(25_000, room.last)
    }

    @Test
    fun `the room collapses toward a chapter's end`() {
        // 10 seconds before chapter three ends: 90s behind, 10s ahead.
        val room = expressibleCorrectionRange(chapters, 290_000)!!

        assertEquals("can be pulled back no further than the text remaining", -10_000, room.first)
        assertEquals("and pushed on by only half of it", 5_000, room.last)
    }

    @Test
    fun `the room collapses toward a chapter's start`() {
        val room = expressibleCorrectionRange(chapters, 210_000)!!

        assertEquals(-5_000, room.first)
        assertEquals(10_000, room.last)
    }

    @Test
    fun `there is no room outside the mapped range or on a boundary`() {
        assertNull(expressibleCorrectionRange(chapters, 200_000)) // exactly on a boundary
        assertNull(expressibleCorrectionRange(chapters, 900_000)) // past the end
        assertNull(expressibleCorrectionRange(chapters, -5_000)) // before the start
        assertNull(expressibleCorrectionRange(listOf(chapters.first()), 250_000))
    }

    /**
     * The property both the D8 bound and the D2 pair rest on: no segment a correction produces may
     * sit more than [ReadAlongMap.RATE_GUARD_RATIO] away from the chapter's own rate. Swept over
     * correction positions and sizes, in both directions, because the exemption means nothing
     * downstream would catch a violation.
     *
     * Every consecutive pair inside the chapter is checked rather than just the correction anchor:
     * a held correction contributes two anchors and three segments, and the unwind between them is
     * the one most likely to run away.
     */
    @Test
    fun `no correction produces an out-of-ratio segment`() {
        for (at in 205_000L..295_000L step 5_000) {
            val room = expressibleCorrectionRange(chapters, at)!!
            for (wanted in longArrayOf(-600_000, -45_000, -5_000, 5_000, 45_000, 600_000)) {
                val correction = correctionFor(base, at, wanted.coerceIn(room))
                val merged = anchorsWithCorrections(chapters, listOf(correction))

                merged.filter { it.absoluteMs in 200_000..300_000 }.zipWithNext { a, b ->
                    val rate = (b.absoluteChars - a.absoluteChars).toDouble() /
                        (b.absoluteMs - a.absoluteMs)
                    val ratio = rate / 0.01
                    assertTrue(
                        "ratio $ratio between ${a.absoluteMs} and ${b.absoluteMs} at $at/$wanted",
                        ratio <= ReadAlongMap.RATE_GUARD_RATIO + 1e-6 &&
                            ratio >= 1.0 / ReadAlongMap.RATE_GUARD_RATIO - 1e-6,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ holding the correction (D2 B)

    /**
     * The measured figure that sent option A back: on the owner's book a correction eroded at 3.2
     * seconds per minute and was gone before the chapter ended. Held flat, it is still whole most of
     * the way through.
     */
    @Test
    fun `the correction is still whole well past where the taper would have eaten it`() {
        val correction = correctionFor(base, 210_000, 20_000)
        val corrected = ReadAlongMap(anchorsWithCorrections(chapters, listOf(correction)))

        // Chapter three runs 200_000..300_000, so the unwind occupies only the last 60s.
        for (at in longArrayOf(215_000, 220_000, 230_000, 239_000)) {
            assertEquals(
                "correction had faded by ${at}ms",
                base.charsForMs(at + 20_000),
                corrected.charsForMs(at),
            )
        }
    }

    @Test
    fun `the correction unwinds to nothing exactly at the chapter boundary`() {
        val correction = correctionFor(base, 210_000, 20_000)
        val corrected = ReadAlongMap(anchorsWithCorrections(chapters, listOf(correction)))

        assertEquals(3_000, corrected.charsForMs(300_000))
        assertEquals(2_000, corrected.charsForMs(200_000))
    }

    /** Two anchors, not one: the correction point and the point it is held until. */
    @Test
    fun `a held correction contributes a pair of anchors`() {
        val correction = correctionFor(base, 210_000, 20_000)

        val owner = anchorsWithCorrections(chapters, listOf(correction)).filter { it.ownerEntered }

        assertEquals(2, owner.size)
        assertEquals(210_000L, owner.first().absoluteMs)
        // Window is three times the 20s correction, so the hold runs until 60s before the end.
        assertEquals(240_000L, owner.last().absoluteMs)
    }

    /** Between the pair the text advances at the chapter's own rate — that is what "held" means. */
    @Test
    fun `the held stretch runs at the chapter's own rate`() {
        val correction = correctionFor(base, 210_000, 20_000)
        val owner = anchorsWithCorrections(chapters, listOf(correction)).filter { it.ownerEntered }

        val held = (owner.last().absoluteChars - owner.first().absoluteChars).toDouble() /
            (owner.last().absoluteMs - owner.first().absoluteMs)

        assertEquals(0.01, held, 1e-6)
    }

    @Test
    fun `a correction with no room to hold falls back to the single anchor`() {
        // 5s before the chapter ends there is nothing to hold flat, so only the one anchor is added.
        val room = expressibleCorrectionRange(chapters, 295_000)!!
        val correction = correctionFor(base, 295_000, 2_000L.coerceIn(room))

        val owner = anchorsWithCorrections(chapters, listOf(correction)).filter { it.ownerEntered }

        assertTrue("expected at most one anchor, got ${owner.size}", owner.size <= 1)
    }

    @Test
    fun `a held correction keeps the map climbing in both coordinates`() {
        for (at in 205_000L..295_000L step 5_000) {
            val room = expressibleCorrectionRange(chapters, at)!!
            for (wanted in longArrayOf(-600_000, -20_000, 20_000, 600_000)) {
                val correction = correctionFor(base, at, wanted.coerceIn(room))
                val merged = anchorsWithCorrections(chapters, listOf(correction))
                merged.zipWithNext { a, b ->
                    assertTrue("ms went backward at $at/$wanted", b.absoluteMs >= a.absoluteMs)
                    assertTrue("chars went backward at $at/$wanted", b.absoluteChars >= a.absoluteChars)
                }
            }
        }
    }

    // ------------------------------------------------------------------ merging into the map

    @Test
    fun `a correction becomes an owner-entered anchor inside its chapter`() {
        val correction = ReadAlongCorrection(audioMs = 250_000, charOffset = 2_800)

        val merged = anchorsWithCorrections(chapters, listOf(correction))

        assertEquals(chapters.size + 1, merged.size)
        val added = merged.single { it.ownerEntered }
        assertEquals(250_000L, added.absoluteMs)
        assertEquals(2_800, added.absoluteChars)
    }

    @Test
    fun `no corrections leaves the anchors exactly as they were`() {
        assertEquals(chapters, anchorsWithCorrections(chapters, emptyList()))
    }

    @Test
    fun `merged anchors climb in both coordinates`() {
        val corrections = listOf(
            ReadAlongCorrection(audioMs = 150_000, charOffset = 1_900),
            ReadAlongCorrection(audioMs = 250_000, charOffset = 2_100),
            ReadAlongCorrection(audioMs = 350_000, charOffset = 3_050),
        )

        val merged = anchorsWithCorrections(chapters, corrections)

        merged.zipWithNext { a, b ->
            assertTrue("ms ran backward: $a then $b", b.absoluteMs >= a.absoluteMs)
            assertTrue("chars ran backward: $a then $b", b.absoluteChars >= a.absoluteChars)
        }
    }

    /**
     * A row can outlive the assumptions it was written under — the whole-book chapter offset can
     * change beneath it, for instance. Clamping is what keeps that from producing an anchor list
     * [ReadAlongMap] would reject or, worse, silently interpolate backward through.
     */
    @Test
    fun `a correction reaching past its own chapter is clamped back inside it`() {
        val beyond = ReadAlongCorrection(audioMs = 250_000, charOffset = 99_999)
        val before = ReadAlongCorrection(audioMs = 150_000, charOffset = -5_000)

        val merged = anchorsWithCorrections(chapters, listOf(before, beyond))

        val late = merged.single { it.absoluteMs == 250_000L }
        val early = merged.single { it.absoluteMs == 150_000L }
        assertEquals("clamped to the end of chapter three", 3_000, late.absoluteChars)
        assertEquals("clamped to the start of chapter two", 1_000, early.absoluteChars)
    }

    @Test
    fun `a correction sitting exactly on a chapter boundary is dropped`() {
        // There is no segment to bend at a boundary, and the boundary is already exact.
        val onBoundary = ReadAlongCorrection(audioMs = 200_000, charOffset = 2_500)

        assertEquals(chapters, anchorsWithCorrections(chapters, listOf(onBoundary)))
    }

    @Test
    fun `a correction outside the mapped range is dropped`() {
        val past = ReadAlongCorrection(audioMs = 900_000, charOffset = 5_000)
        val before = ReadAlongCorrection(audioMs = -50_000, charOffset = 10)

        assertEquals(chapters, anchorsWithCorrections(chapters, listOf(past, before)))
    }

    /** End to end: the corrected map actually shows the text the correction asked for. */
    @Test
    fun `the corrected map reads back the position the owner entered`() {
        val correction = correctionFor(base, anchorMs, 3 * NUDGE_STEP_MS)
        val corrected = ReadAlongMap(anchorsWithCorrections(chapters, listOf(correction)))

        assertEquals(correction.charOffset, corrected.charsForMs(anchorMs))
        // ...and the chapter it sits in still starts and ends exactly where it did.
        assertEquals(2_000, corrected.charsForMs(200_000))
        assertEquals(3_000, corrected.charsForMs(300_000))
    }
}
