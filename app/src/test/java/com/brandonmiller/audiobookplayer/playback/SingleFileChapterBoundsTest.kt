package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `.m4b` branch of `chapterTimeline()` has to produce a book that behaves identically to the
 * folder book with the same chapters — that was the bet `add-transport-controls` made when
 * [BookTimeline] was built to accept both shapes, and this is where it is collected.
 *
 * So every case here runs the same operation twice: once on bounds built from a starts array (one
 * media item, chapters at offsets) and once on bounds shaped like a folder playlist (one media item
 * per chapter, each starting at zero), and asserts the two agree once translated. Anywhere they
 * disagree, an `.m4b` book would behave differently from a folder book for no reason the user
 * could see.
 */
class SingleFileChapterBoundsTest {

    /** A four-chapter book: 10, 15, 15, and 20 minutes. */
    private val startsMs = longArrayOf(0, 600_000, 1_500_000, 2_400_000)
    private val durationMs = 3_600_000L
    private val chapterDurations = listOf(600_000L, 900_000L, 900_000L, 1_200_000L)

    private val singleFile = BookTimeline(chapterBoundsFrom(startsMs, durationMs))

    private val folder = BookTimeline(
        chapterDurations.mapIndexed { index, duration ->
            ChapterBounds(chapterIndex = index, mediaItemIndex = index, startInItemMs = 0, endInItemMs = duration)
        },
    )

    @Test
    fun `bounds chain each end from the next start and take the last from the book duration`() {
        assertEquals(
            listOf(
                ChapterBounds(0, 0, 0, 600_000),
                ChapterBounds(1, 0, 600_000, 1_500_000),
                ChapterBounds(2, 0, 1_500_000, 2_400_000),
                ChapterBounds(3, 0, 2_400_000, 3_600_000),
            ),
            chapterBoundsFrom(startsMs, durationMs),
        )
    }

    @Test
    fun `locating a position finds the same chapter and offset in both shapes`() {
        forEachChapterOffset { chapterIndex, offsetMs ->
            assertEquals(
                "locate at chapter $chapterIndex + ${offsetMs}ms",
                folder.locate(chapterIndex, offsetMs),
                singleFile.locate(0, startsMs[chapterIndex] + offsetMs),
            )
        }
    }

    @Test
    fun `absolute position agrees across both shapes`() {
        forEachChapterOffset { chapterIndex, offsetMs ->
            val location = Location(chapterIndex, offsetMs)
            assertEquals(
                "absolute position at chapter $chapterIndex + ${offsetMs}ms",
                folder.absolutePosition(location),
                singleFile.absolutePosition(location),
            )
        }
    }

    @Test
    fun `total duration agrees across both shapes`() {
        assertEquals(durationMs, singleFile.totalDurationMs())
        assertEquals(folder.totalDurationMs(), singleFile.totalDurationMs())
    }

    @Test
    fun `every fixed-interval seek lands at the same book position in both shapes`() {
        val deltas = listOf(-3_600_000L, -60_000L, -10_000L, 10_000L, 60_000L, 3_600_000L)
        forEachChapterOffset { chapterIndex, offsetMs ->
            val from = Location(chapterIndex, offsetMs)
            for (delta in deltas) {
                assertSameBookPosition(
                    "seek $delta from chapter $chapterIndex + ${offsetMs}ms",
                    folder.seekTarget(from, delta),
                    singleFile.seekTarget(from, delta),
                )
            }
        }
    }

    @Test
    fun `previous chapter obeys the 3-second rule identically in both shapes`() {
        forEachChapterOffset { chapterIndex, offsetMs ->
            val from = Location(chapterIndex, offsetMs)
            assertSameBookPosition(
                "previous from chapter $chapterIndex + ${offsetMs}ms",
                folder.previousChapterTarget(from),
                singleFile.previousChapterTarget(from),
            )
        }
    }

    @Test
    fun `next chapter agrees, and both stop at the last chapter`() {
        forEachChapterOffset { chapterIndex, offsetMs ->
            val from = Location(chapterIndex, offsetMs)
            val expected = folder.nextChapterTarget(from)
            val actual = singleFile.nextChapterTarget(from)
            if (expected == null) {
                assertNull("next from the final chapter does nothing", actual)
            } else {
                assertSameBookPosition("next from chapter $chapterIndex", expected, actual!!)
            }
        }
    }

    @Test
    fun `the scrubber maps an absolute position to the same place in both shapes`() {
        val absolutePositions = listOf(0L, 1L, 599_999L, 600_000L, 1_800_000L, 3_599_999L, 3_600_000L, 9_999_999L)
        for (absoluteMs in absolutePositions) {
            assertSameBookPosition(
                "scrubbing to ${absoluteMs}ms",
                folder.targetForAbsolute(absoluteMs),
                singleFile.targetForAbsolute(absoluteMs),
            )
        }
    }

    @Test
    fun `at an exact chapter boundary both shapes report the same book position`() {
        // The one instant the two disagree about, and only about naming. A folder book at
        // position 600,000 of item 0 is sitting on the last frame of chapter 1 — a state ExoPlayer
        // leaves immediately by transitioning to the next item — while a single-file book at
        // absolute 600,000 is already at the start of chapter 2. Both are the same moment of audio,
        // and every operation the user can see goes through the book-wide position, which agrees.
        val boundaryMs = 600_000L

        val folderLocation = folder.locate(0, boundaryMs)
        val singleFileLocation = singleFile.locate(0, boundaryMs)

        assertEquals(Location(chapterIndex = 0, offsetMs = boundaryMs), folderLocation)
        assertEquals(Location(chapterIndex = 1, offsetMs = 0), singleFileLocation)
        assertEquals(
            folder.absolutePosition(folderLocation),
            singleFile.absolutePosition(singleFileLocation),
        )
    }

    @Test
    fun `a one-chapter book of unknown duration leaves its end unknown rather than zero`() {
        // What an `.m4b` whose duration could not be read produces: one chapter, no known end.
        val bounds = chapterBoundsFrom(longArrayOf(0), bookDurationMs = 0)

        assertEquals(listOf(ChapterBounds(0, 0, 0, null)), bounds)
        // BookTimeline's documented behavior for an unknown end: clamp rather than guess.
        assertEquals(PlayerTarget(0, 10_000), BookTimeline(bounds).seekTarget(Location(0, 0), 10_000))
    }

    @Test
    fun `a one-chapter book spanning the whole file scrubs across its full duration`() {
        val timeline = BookTimeline(chapterBoundsFrom(longArrayOf(0), bookDurationMs = 46_800_000))

        assertEquals(46_800_000L, timeline.totalDurationMs())
        assertEquals(PlayerTarget(0, 23_400_000), timeline.targetForAbsolute(23_400_000))
        // The only chapter is also the first, so previous restarts it rather than doing nothing.
        assertEquals(PlayerTarget(0, 0), timeline.previousChapterTarget(Location(0, 1_000)))
        assertNull(timeline.nextChapterTarget(Location(0, 1_000)))
    }

    /**
     * A spread of offsets inside every chapter: its start, either side of the 3-second rule, the
     * middle, and the last millisecond before it ends.
     *
     * The exact end is excluded deliberately, and covered on its own below: it is the one instant
     * the two shapes name differently, because it belongs to two chapters at once.
     */
    private fun forEachChapterOffset(check: (chapterIndex: Int, offsetMs: Long) -> Unit) {
        chapterDurations.forEachIndexed { chapterIndex, duration ->
            listOf(0L, 1_000L, 3_001L, duration / 2, duration - 1).forEach { offsetMs ->
                check(chapterIndex, offsetMs)
            }
        }
    }

    /**
     * A folder target names a media item and an offset within it; a single-file target names item
     * zero and an absolute position. Both are the same place in the book, expressed in each shape's
     * own coordinates, so comparison goes through the book-wide position.
     */
    private fun assertSameBookPosition(message: String, folderTarget: PlayerTarget, singleFileTarget: PlayerTarget) {
        val expectedAbsolute = chapterDurations.take(folderTarget.mediaItemIndex).sum() + folderTarget.positionMs
        assertEquals(message, 0, singleFileTarget.mediaItemIndex)
        assertEquals(message, expectedAbsolute, singleFileTarget.positionMs)
    }
}
