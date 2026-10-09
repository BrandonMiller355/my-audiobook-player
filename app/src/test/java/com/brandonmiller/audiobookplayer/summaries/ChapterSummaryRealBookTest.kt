package com.brandonmiller.audiobookplayer.summaries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's Brothers Karamazov summary file, against chapter titles shaped like the audiobook's
 * ("Book 2 - Chapter 1"). Twelve books and an epilogue, each numbering its chapters from one.
 */
class ChapterSummaryRealBookTest {

    private val file = checkNotNull(javaClass.getResource("/summaries/brothers_karamazov.md")).readText()

    private val chaptersPerBook = listOf(5, 8, 11, 7, 7, 3, 4, 8, 9, 7, 10, 14)

    private val titles: Map<Int, String> = buildList {
        add("The Brothers Karamazov")
        chaptersPerBook.forEachIndexed { book, count ->
            (1..count).forEach { add("Book ${book + 1} - Chapter $it") }
        }
        (1..3).forEach { add("Epilogue - Chapter $it") }
    }.withIndex().associate { it.index to it.value }

    @Test
    fun `every chapter in the file is read`() {
        val entries = parseSummaryFile(file)

        assertEquals(chaptersPerBook.sum() + 3, entries.size)
        assertTrue(entries.all { it.section != null })
    }

    @Test
    fun `every chapter in the file lands on its own book's chapter`() {
        val match = matchSummaries(parseSummaryFile(file), titles)

        assertEquals(chaptersPerBook.sum() + 3, match.matchedCount)
        val book2Chapter1 = titles.entries.single { it.value == "Book 2 - Chapter 1" }.key
        assertTrue(match.byChapterIndex.getValue(book2Chapter1).contains("monastery", ignoreCase = true))
    }
}
