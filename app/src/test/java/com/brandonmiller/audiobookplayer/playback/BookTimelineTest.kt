package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookTimelineTest {

    // Five chapters, durations mirroring a small folder book: 100s, 200s, 150s, 300s, 50s.
    private val folderDurations = listOf(100_000L, 200_000L, 150_000L, 300_000L, 50_000L)

    private fun folderBounds(durations: List<Long> = folderDurations): List<ChapterBounds> =
        durations.mapIndexed { index, duration -> ChapterBounds(index, index, 0, duration) }

    // Same five chapters, but as sub-ranges of one shared item — the .m4b shape.
    private fun m4bBounds(durations: List<Long> = folderDurations): List<ChapterBounds> {
        var cursor = 0L
        return durations.mapIndexed { index, duration ->
            val bound = ChapterBounds(index, 0, cursor, cursor + duration)
            cursor += duration
            bound
        }
    }

    // ------------------------------------------------------------------ locate

    @Test
    fun `locate finds the chapter a folder position falls within`() {
        val timeline = BookTimeline(folderBounds())

        assertEquals(Location(2, 50_000), timeline.locate(mediaItemIndex = 2, positionMs = 50_000))
    }

    @Test
    fun `locate finds the chapter an m4b position falls within`() {
        val timeline = BookTimeline(m4bBounds())

        // Chapter 2 starts at 100_000 + 200_000 = 300_000.
        assertEquals(Location(2, 50_000), timeline.locate(mediaItemIndex = 0, positionMs = 350_000))
    }

    // ------------------------------------------------------------------ seekTarget: within a chapter

    @Test
    fun `seek within the current chapter does not cross a boundary`() {
        for (bounds in listOf(folderBounds(), m4bBounds())) {
            val timeline = BookTimeline(bounds)
            val target = timeline.seekTarget(Location(chapterIndex = 1, offsetMs = 50_000), deltaMs = 10_000)
            assertEquals(bounds[1].mediaItemIndex, target.mediaItemIndex)
            assertEquals(bounds[1].startInItemMs + 60_000, target.positionMs)
        }
    }

    // ------------------------------------------------------------------ seekTarget: forward across chapters

    @Test
    fun `forward seek crosses into the next chapter for a folder book`() {
        val timeline = BookTimeline(folderBounds())

        // Chapter 0 is 100s; 10s in, +95s overflows by 5s into chapter 1.
        val target = timeline.seekTarget(Location(0, 10_000), deltaMs = 95_000)

        assertEquals(1, target.mediaItemIndex)
        assertEquals(5_000L, target.positionMs)
    }

    @Test
    fun `forward seek crosses into the next chapter for an m4b book`() {
        val timeline = BookTimeline(m4bBounds())

        val target = timeline.seekTarget(Location(0, 10_000), deltaMs = 95_000)

        // Chapter 1 starts at absolute 100_000; overflow lands 5s into it.
        assertEquals(0, target.mediaItemIndex)
        assertEquals(105_000L, target.positionMs)
    }

    @Test
    fun `forward seek can cross more than one chapter boundary`() {
        val timeline = BookTimeline(folderBounds())

        // Chapter 0 (100s) + chapter 1 (200s) = 300s; starting 10s in and adding 295s lands
        // 5s into chapter 2.
        val target = timeline.seekTarget(Location(0, 10_000), deltaMs = 295_000)

        assertEquals(2, target.mediaItemIndex)
        assertEquals(5_000L, target.positionMs)
    }

    @Test
    fun `forward seek at the last chapter clamps to the end of the book`() {
        val timeline = BookTimeline(folderBounds())

        // Chapter 4 (index 4) is 50s long; seeking well past its end clamps rather than erroring.
        val target = timeline.seekTarget(Location(4, 40_000), deltaMs = 100_000)

        assertEquals(4, target.mediaItemIndex)
        assertEquals(50_000L, target.positionMs)
    }

    @Test
    fun `forward seek clamps to chapter end when the boundary is not yet known`() {
        // Chapter 1's duration is unresolved (null) — cannot know where chapter 2 begins.
        val bounds = listOf(
            ChapterBounds(0, 0, 0, 100_000),
            ChapterBounds(1, 1, 0, null),
            ChapterBounds(2, 2, 0, 150_000),
        )
        val timeline = BookTimeline(bounds)

        val target = timeline.seekTarget(Location(1, 50_000), deltaMs = 60_000)

        // With no known end, BookTimeline cannot compute an overflow into chapter 2 — it stays
        // within chapter 1's item rather than guessing where the next one starts. If the item's
        // real duration turns out to be shorter than the requested position, Player.seekTo clamps
        // it there itself; BookTimeline does not need to duplicate that.
        assertEquals(1, target.mediaItemIndex)
        assertEquals(110_000L, target.positionMs)
    }

    // ------------------------------------------------------------------ seekTarget: backward across chapters

    @Test
    fun `backward seek crosses into the previous chapter for a folder book`() {
        val timeline = BookTimeline(folderBounds())

        // Chapter 1, 10s in; seeking back 30s needs 20s from chapter 0, landing 80s into it.
        val target = timeline.seekTarget(Location(1, 10_000), deltaMs = -30_000)

        assertEquals(0, target.mediaItemIndex)
        assertEquals(80_000L, target.positionMs)
    }

    @Test
    fun `backward seek crosses into the previous chapter for an m4b book`() {
        val timeline = BookTimeline(m4bBounds())

        val target = timeline.seekTarget(Location(1, 10_000), deltaMs = -30_000)

        // Chapter 0 spans absolute 0..100_000; landing 80s in is absolute 80_000.
        assertEquals(0, target.mediaItemIndex)
        assertEquals(80_000L, target.positionMs)
    }

    @Test
    fun `backward seek at the start of the book clamps to zero`() {
        val timeline = BookTimeline(folderBounds())

        val target = timeline.seekTarget(Location(0, 5_000), deltaMs = -60_000)

        assertEquals(0, target.mediaItemIndex)
        assertEquals(0L, target.positionMs)
    }

    // ------------------------------------------------------------------ previous chapter: 3-second rule

    @Test
    fun `previous chapter restarts the current chapter well past the tolerance`() {
        for (bounds in listOf(folderBounds(), m4bBounds())) {
            val timeline = BookTimeline(bounds)
            val target = timeline.previousChapterTarget(Location(2, 10_000))
            assertEquals(bounds[2].mediaItemIndex, target.mediaItemIndex)
            assertEquals(bounds[2].startInItemMs, target.positionMs)
        }
    }

    @Test
    fun `previous chapter moves back within the tolerance`() {
        val timeline = BookTimeline(folderBounds())

        val target = timeline.previousChapterTarget(Location(2, 2_000))

        assertEquals(1, target.mediaItemIndex)
        assertEquals(0L, target.positionMs)
    }

    @Test
    fun `previous chapter is exactly at the tolerance boundary`() {
        val timeline = BookTimeline(folderBounds())

        // Exactly 3000ms is not "more than" the tolerance, so it still goes back.
        val target = timeline.previousChapterTarget(Location(2, 3_000), toleranceMs = 3_000)

        assertEquals(1, target.mediaItemIndex)
    }

    @Test
    fun `previous chapter at the first chapter restarts it rather than doing nothing`() {
        val timeline = BookTimeline(folderBounds())

        val target = timeline.previousChapterTarget(Location(0, 500))

        assertEquals(0, target.mediaItemIndex)
        assertEquals(0L, target.positionMs)
    }

    @Test
    fun `previous chapter rule applies identically to both book shapes`() {
        val folder = BookTimeline(folderBounds())
        val m4b = BookTimeline(m4bBounds())

        val folderTarget = folder.previousChapterTarget(Location(3, 1_000))
        val m4bTarget = m4b.previousChapterTarget(Location(3, 1_000))

        assertEquals(2, folderTarget.mediaItemIndex)
        assertEquals(0, m4bTarget.mediaItemIndex)
        // Chapter 2 starts at absolute 300_000 in the m4b shape.
        assertEquals(300_000L, m4bTarget.positionMs)
    }

    // ------------------------------------------------------------------ next chapter

    @Test
    fun `next chapter targets the following chapter's start`() {
        val timeline = BookTimeline(folderBounds())

        val target = timeline.nextChapterTarget(Location(1, 50_000))

        assertEquals(2, target?.mediaItemIndex)
        assertEquals(0L, target?.positionMs)
    }

    @Test
    fun `next chapter is null at the final chapter`() {
        val timeline = BookTimeline(folderBounds())

        assertNull(timeline.nextChapterTarget(Location(4, 10_000)))
    }

    // ------------------------------------------------------------------ scrubber: absolute position

    @Test
    fun `absolute position sums every prior chapter's duration plus the current offset`() {
        val timeline = BookTimeline(folderBounds())

        // Chapters 0 (100s) + 1 (200s) = 300s, plus 20s into chapter 2.
        assertEquals(320_000L, timeline.absolutePosition(Location(2, 20_000)))
    }

    @Test
    fun `absolute position at the very start of the book is zero`() {
        val timeline = BookTimeline(folderBounds())

        assertEquals(0L, timeline.absolutePosition(Location(0, 0)))
    }

    @Test
    fun `unresolved chapter durations contribute zero to absolute position`() {
        val bounds = listOf(
            ChapterBounds(0, 0, 0, 100_000),
            ChapterBounds(1, 1, 0, null), // not yet known
            ChapterBounds(2, 2, 0, 150_000),
        )
        val timeline = BookTimeline(bounds)

        // Chapter 1's duration is unknown, so it contributes 0 rather than blocking the sum.
        assertEquals(100_000L, timeline.absolutePosition(Location(2, 0)))
    }

    @Test
    fun `total duration is the sum of every known chapter, undercounting unresolved ones`() {
        val known = BookTimeline(folderBounds())
        assertEquals(folderDurations.sum(), known.totalDurationMs())

        val partiallyKnown = BookTimeline(
            listOf(
                ChapterBounds(0, 0, 0, 100_000),
                ChapterBounds(1, 1, 0, null),
            ),
        )
        assertEquals(100_000L, partiallyKnown.totalDurationMs())
    }

    @Test
    fun `target for absolute position maps back into the right chapter`() {
        val timeline = BookTimeline(folderBounds())

        // 320s: 100 (ch0) + 200 (ch1) = 300, so 20s into chapter 2.
        val target = timeline.targetForAbsolute(320_000)

        assertEquals(2, target.mediaItemIndex)
        assertEquals(20_000L, target.positionMs)
    }

    @Test
    fun `target for absolute position beyond the known total clamps to the last known chapter`() {
        val timeline = BookTimeline(folderBounds())

        val target = timeline.targetForAbsolute(10_000_000)

        assertEquals(4, target.mediaItemIndex)
        assertEquals(50_000L, target.positionMs)
    }

    @Test
    fun `absolute position and target for absolute round-trip for an m4b book`() {
        val timeline = BookTimeline(m4bBounds())

        val absolute = timeline.absolutePosition(Location(2, 20_000))
        val target = timeline.targetForAbsolute(absolute)

        assertEquals(0, target.mediaItemIndex)
        assertEquals(absolute, target.positionMs) // single item: position IS the absolute value
    }
}
