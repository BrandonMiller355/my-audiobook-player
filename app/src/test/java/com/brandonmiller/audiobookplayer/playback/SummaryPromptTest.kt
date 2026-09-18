package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What arms the end-of-chapter offer, and — mostly — what does not (`add-chapter-summaries`
 * design D6).
 *
 * Every case here is a pair of consecutive position samples. The book is a notional one whose
 * chapters run ten minutes each, so chapter `n` starts at `n * 600_000`.
 */
class SummaryPromptTest {

    private fun sample(chapterIndex: Int, intoChapterMs: Long, bookId: Long = 1) = PlaybackSample(
        bookId = bookId,
        chapterIndex = chapterIndex,
        absolutePositionMs = chapterIndex * 600_000L + intoChapterMs,
    )

    @Test
    fun `listening through a boundary arms the offer for the chapter that ended`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 599_800)
        val after = sample(chapterIndex = 3, intoChapterMs = 300)

        assertEquals(2, chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `a missed tick under load still arms it`() {
        // The ticker runs at 500ms; three seconds is several missed ticks at the fastest speed stop.
        val before = sample(chapterIndex = 2, intoChapterMs = 598_000)
        val after = sample(chapterIndex = 3, intoChapterMs = 1_000)

        assertEquals(2, chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `a seek forward across a boundary does not arm it`() {
        // The +10s button, pressed just before the boundary. The index advances by one exactly as a
        // genuine crossing does -- the size of the move is the only thing that distinguishes them.
        val before = sample(chapterIndex = 2, intoChapterMs = 595_000)
        val after = sample(chapterIndex = 3, intoChapterMs = 5_000)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `skipping to the next chapter does not arm it`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 120_000)
        val after = sample(chapterIndex = 3, intoChapterMs = 0)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `scrubbing far forward does not arm it`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 30_000)
        val after = sample(chapterIndex = 3, intoChapterMs = 400_000)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `scrubbing several chapters forward does not arm it`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 30_000)
        val after = sample(chapterIndex = 9, intoChapterMs = 10_000)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `moving backward does not arm it`() {
        val before = sample(chapterIndex = 3, intoChapterMs = 1_000)
        val after = sample(chapterIndex = 2, intoChapterMs = 599_000)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `the previous chapter control at the start of a chapter does not arm it`() {
        val before = sample(chapterIndex = 3, intoChapterMs = 2_000)
        val after = sample(chapterIndex = 2, intoChapterMs = 0)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `resuming a book does not arm it`() {
        // No predecessor at all: the first sample after the controller connects.
        assertNull(chapterFinishedBy(null, sample(chapterIndex = 3, intoChapterMs = 0), isPlaying = true))
    }

    @Test
    fun `a paused crossing does not arm it`() {
        // Selecting a chapter from the list while paused moves the index with nothing being heard.
        val before = sample(chapterIndex = 2, intoChapterMs = 599_800)
        val after = sample(chapterIndex = 3, intoChapterMs = 300)

        assertNull(chapterFinishedBy(before, after, isPlaying = false))
    }

    @Test
    fun `a different book is not a crossing`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 599_800, bookId = 1)
        val after = sample(chapterIndex = 3, intoChapterMs = 300, bookId = 2)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    @Test
    fun `playing on within a chapter arms nothing`() {
        val before = sample(chapterIndex = 2, intoChapterMs = 120_000)
        val after = sample(chapterIndex = 2, intoChapterMs = 120_500)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }

    /**
     * The book ends rather than crossing into anything, so the last chapter's summary is never
     * offered. Stated here because it is a known gap in design D6, not an accident — the chapter list
     * still carries it.
     */
    @Test
    fun `the end of the last chapter arms nothing`() {
        val before = sample(chapterIndex = 9, intoChapterMs = 599_500)
        val after = sample(chapterIndex = 9, intoChapterMs = 600_000)

        assertNull(chapterFinishedBy(before, after, isPlaying = true))
    }
}
