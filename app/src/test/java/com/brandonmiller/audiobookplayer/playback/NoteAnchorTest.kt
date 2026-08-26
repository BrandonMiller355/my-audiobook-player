package com.brandonmiller.audiobookplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lead-in, and the rule that decides which chapter title a note carries.
 *
 * The second half is the point. Anchor arithmetic that is right while the recorded title comes from
 * the tap position produces notes that name a chapter they do not seek into, and that failure is
 * invisible except on the marks taken just after a chapter boundary — which are exactly the marks a
 * lead-in creates. `a mark just after a boundary is labeled with the previous chapter` is the test
 * standing between design D5 and shipping that.
 */
class NoteAnchorTest {

    // Five chapters: 100s, 200s, 150s, 300s, 50s — the same shape BookTimelineTest uses.
    private val durations = listOf(100_000L, 200_000L, 150_000L, 300_000L, 50_000L)

    private val titles = listOf("Prologue", "Chapter 1", "Chapter 2", "Chapter 3", "Epilogue")

    private fun folderTimeline() = BookTimeline(
        durations.mapIndexed { index, duration -> ChapterBounds(index, index, 0, duration) },
    )

    private fun m4bTimeline(): BookTimeline {
        var cursor = 0L
        return BookTimeline(
            durations.mapIndexed { index, duration ->
                ChapterBounds(index, 0, cursor, cursor + duration).also { cursor += duration }
            },
        )
    }

    private fun anchor(timeline: BookTimeline, from: Location) =
        noteAnchorFor(timeline, from, titles)

    @Test
    fun `a mark well inside a chapter anchors the lead-in behind the tap`() {
        val result = anchor(folderTimeline(), Location(chapterIndex = 3, offsetMs = 120_000))

        assertEquals(PlayerTarget(3, 120_000 - NOTE_LEAD_IN_MS), result.target)
        assertEquals("Chapter 3", result.chapterTitle)
    }

    /**
     * The consequence design D5 says not to miss: marking just after a boundary is *supposed* to
     * land in the previous chapter, and the label has to agree with the anchor.
     */
    @Test
    fun `a mark just after a boundary is labeled with the previous chapter`() {
        // Five seconds into chapter 2, whose previous chapter runs 200s.
        val result = anchor(folderTimeline(), Location(chapterIndex = 2, offsetMs = 5_000))

        assertEquals("rolls back into chapter 1's tail", PlayerTarget(1, 190_000), result.target)
        assertEquals("Chapter 1", result.chapterTitle)
    }

    @Test
    fun `a mark near the start of the book clamps at zero rather than going negative`() {
        val result = anchor(folderTimeline(), Location(chapterIndex = 0, offsetMs = 4_000))

        assertEquals(PlayerTarget(0, 0), result.target)
        assertEquals("Prologue", result.chapterTitle)
    }

    /**
     * `seekTarget` refuses to guess how far back an unmeasured chapter extends, so the anchor stops
     * at the current chapter's start. The label must follow it there rather than naming the previous
     * chapter the note does not actually reach.
     */
    @Test
    fun `a mark near a boundary with the previous duration unknown clamps at the chapter start`() {
        val timeline = BookTimeline(
            listOf(
                ChapterBounds(0, 0, 0, null), // not yet resolved
                ChapterBounds(1, 1, 0, 200_000),
                ChapterBounds(2, 2, 0, 150_000),
            ),
        )

        val result = noteAnchorFor(timeline, Location(chapterIndex = 1, offsetMs = 3_000), titles)

        assertEquals(PlayerTarget(1, 0), result.target)
        assertEquals("Chapter 1", result.chapterTitle)
    }

    @Test
    fun `the anchor is exactly the lead-in behind the tap, not a rounded figure`() {
        val result = anchor(folderTimeline(), Location(chapterIndex = 1, offsetMs = 137_412))

        assertEquals(137_412 - 15_000, result.target.positionMs)
    }

    // ------------------------------------------------------------------ single-file books

    @Test
    fun `an m4b mark keeps every chapter on media item zero`() {
        // Chapter 3 starts at 450_000 absolute; 120s in is 570_000.
        val result = anchor(m4bTimeline(), Location(chapterIndex = 3, offsetMs = 120_000))

        assertEquals(PlayerTarget(0, 570_000 - NOTE_LEAD_IN_MS), result.target)
        assertEquals("Chapter 3", result.chapterTitle)
    }

    @Test
    fun `an m4b mark just after a boundary rolls into the previous chapter's tail`() {
        // Chapter 2 starts at 300_000 absolute.
        val result = anchor(m4bTimeline(), Location(chapterIndex = 2, offsetMs = 5_000))

        assertEquals(PlayerTarget(0, 290_000), result.target)
        assertEquals("Chapter 1", result.chapterTitle)
    }

    @Test
    fun `an m4b mark at the very start of the book clamps at zero`() {
        val result = anchor(m4bTimeline(), Location(chapterIndex = 0, offsetMs = 1_000))

        assertEquals(PlayerTarget(0, 0), result.target)
        assertEquals("Prologue", result.chapterTitle)
    }

    // ------------------------------------------------------------------ degenerate input

    @Test
    fun `a book whose chapter rows have not loaded still produces a usable anchor`() {
        // The anchor is the part that cannot be recovered later; an absent title is survivable.
        val result = noteAnchorFor(folderTimeline(), Location(2, 50_000), chapterTitles = emptyList())

        assertEquals(PlayerTarget(2, 35_000), result.target)
        assertEquals("", result.chapterTitle)
    }
}
