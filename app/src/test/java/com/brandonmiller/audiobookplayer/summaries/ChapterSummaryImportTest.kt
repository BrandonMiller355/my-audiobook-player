package com.brandonmiller.audiobookplayer.summaries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSummaryImportTest {

    // ------------------------------------------------------------------ marker recognition

    @Test
    fun `a bare chapter reference is a marker`() {
        assertEquals("Chapter 1", chapterReferenceIn("Chapter 1"))
        assertEquals("Chapter 12", chapterReferenceIn("Chapter 12:"))
        assertEquals("Chapter IX", chapterReferenceIn("Chapter IX"))
        assertEquals("Chapter Twelve", chapterReferenceIn("Chapter Twelve"))
        assertEquals("Prologue", chapterReferenceIn("Prologue"))
        assertEquals("EPILOGUE", chapterReferenceIn("  EPILOGUE  "))
    }

    @Test
    fun `a marker may carry a title after a separator`() {
        // What an assistant emits by default. Everything past the separator is discarded; the label
        // that survives is what gets normalized.
        assertEquals("Chapter 1", chapterReferenceIn("Chapter 1: The Well of Ascension"))
        assertEquals("Chapter 4", chapterReferenceIn("Chapter 4 — Vin's Descent"))
    }

    @Test
    fun `prose that opens with a chapter reference is not a marker`() {
        // The whole defense of this format. normalizeChapterLabel reads this as chapter four on its
        // own; the length test is what rejects it.
        assertNull(chapterReferenceIn("Chapter 4 was where the heist turned"))
        assertNull(chapterReferenceIn("Chapter 4 was where the heist turned: Vin knew it too"))
    }

    @Test
    fun `ordinary prose is not a marker`() {
        assertNull(chapterReferenceIn("Vin considers the Deepness."))
        assertNull(chapterReferenceIn(""))
        assertNull(chapterReferenceIn("   "))
        assertNull(chapterReferenceIn("Acknowledgments"))
        assertNull(chapterReferenceIn("Part One"))
    }

    @Test
    fun `a title that merely contains a number is not a chapter reference`() {
        // The owner's own file opens with the first of these. normalizeChapterLabel reads it as
        // chapter three on its own -- correct for a table-of-contents entry, and catastrophic here,
        // because the document title would take chapter three's slot and the front matter beneath it
        // would become that chapter's summary.
        assertNull(chapterReferenceIn("Mistborn Book 3: The Hero of Ages — Chapter Summaries"))
        assertNull(chapterReferenceIn("Mistborn Book 3"))
        assertNull(chapterReferenceIn("Part 2: Ascension"))
    }

    @Test
    fun `a bare number standing alone is still a chapter reference`() {
        assertEquals("12", chapterReferenceIn("12"))
        assertEquals("IX", chapterReferenceIn("IX"))
    }

    // ------------------------------------------------------------------ parsing

    @Test
    fun `entries are split at their markers`() {
        val entries = parseSummaryFile(
            """
            Chapter 1
            Vin considers the Deepness.

            It runs to more than one paragraph.

            Chapter 2
            Kelsier recruits the crew.
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals("Chapter 1", entries[0].label)
        assertEquals("Vin considers the Deepness.\n\nIt runs to more than one paragraph.", entries[0].text)
        assertEquals("Chapter 2", entries[1].label)
        assertEquals("Kelsier recruits the crew.", entries[1].text)
    }

    @Test
    fun `text before the first marker is discarded`() {
        val entries = parseSummaryFile(
            """
            Here are the chapter summaries you asked for. Let me know if you would like them longer.

            Chapter 1
            Vin considers the Deepness.
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("Vin considers the Deepness.", entries[0].text)
    }

    @Test
    fun `a summary keeps a line of its own prose that opens with a chapter reference`() {
        val entries = parseSummaryFile(
            """
            Chapter 3
            The heist begins.
            Chapter 4 was where it turned, but that comes later.
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertTrue(entries[0].text.contains("Chapter 4 was where it turned"))
    }

    @Test
    fun `carriage returns do not defeat marker recognition`() {
        val entries = parseSummaryFile("Chapter 1:\r\nVin considers the Deepness.\r\n")

        assertEquals(1, entries.size)
        assertEquals("Chapter 1", entries[0].label)
        assertEquals("Vin considers the Deepness.", entries[0].text)
    }

    @Test
    fun `a file with no markers yields nothing`() {
        assertTrue(parseSummaryFile("").isEmpty())
        assertTrue(parseSummaryFile("Just some notes about the book.\nNothing structured.").isEmpty())
    }

    @Test
    fun `a marker with no body beneath it is dropped`() {
        val entries = parseSummaryFile(
            """
            Chapter 1

            Chapter 2
            Kelsier recruits the crew.
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("Chapter 2", entries[0].label)
    }

    // ------------------------------------------------------------------ markdown

    @Test
    fun `a markdown heading is a marker at any level`() {
        assertEquals(SummaryLine.Marker("Chapter 1"), classifySummaryLine("# Chapter 1"))
        assertEquals(SummaryLine.Marker("Chapter 1"), classifySummaryLine("## Chapter 1"))
        assertEquals(SummaryLine.Marker("Chapter 1"), classifySummaryLine("### Chapter 1"))
        assertEquals(SummaryLine.Marker("Prologue"), classifySummaryLine("## Prologue"))
        assertEquals(SummaryLine.Marker("Chapter 1"), classifySummaryLine("### **Chapter 1**"))
        assertEquals(SummaryLine.Marker("Chapter 1"), classifySummaryLine("## Chapter 1 ##"))
    }

    @Test
    fun `a heading that names no chapter is structural, not prose`() {
        // The two in the owner's own file. Neither is an entry, and neither belongs to the summary
        // above it.
        assertEquals(SummaryLine.StructuralHeading, classifySummaryLine("# Mistborn Book 3: The Hero of Ages — Chapter Summaries"))
        assertEquals(SummaryLine.StructuralHeading, classifySummaryLine("## Part One: Legacy of the Survivor"))
    }

    @Test
    fun `a bold label at the start of prose is not a marker`() {
        // Every non-heading line in the owner's file that begins with ** is one of these.
        assertEquals(SummaryLine.Body, classifySummaryLine("**Opening epigraph:** An unidentified narrator claims to be the Hero."))
        assertEquals(SummaryLine.Body, classifySummaryLine("**Author:** Brandon Sanderson"))
    }

    @Test
    fun `a part heading does not join the summary above it`() {
        val entries = parseSummaryFile(
            """
            # The Hero of Ages — Chapter Summaries

            **Author:** Brandon Sanderson

            ## Prologue

            Marsh is trapped inside his own body.

            ## Part One: Legacy of the Survivor

            ### Chapter 1

            Fatren prepares for an attack by koloss.
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals("Prologue", entries[0].label)
        assertEquals("Marsh is trapped inside his own body.", entries[0].text)
        assertEquals("Chapter 1", entries[1].label)
        assertEquals("Fatren prepares for an attack by koloss.", entries[1].text)
    }

    @Test
    fun `front matter between the title and the first chapter is discarded`() {
        val entries = parseSummaryFile(
            """
            # Chapter Summaries

            **Source:** The EPUB in this project's folder.
            Each summary covers the main events through the end of that chapter.

            ## Chapter 1
            Vin considers the Deepness.
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("Vin considers the Deepness.", entries[0].text)
    }

    // ------------------------------------------------------------------ matching

    private fun entries(vararg pairs: Pair<String, String>) =
        pairs.map { (label, text) -> SummaryEntry(label, text) }

    @Test
    fun `a prologue occupying chapter one does not shift the rest`() {
        // The case the whole matcher exists for: the audio counts the prologue, the file does not.
        val chapters = mapOf(0 to "Prologue", 1 to "Chapter 1", 2 to "Chapter 2")
        val match = matchSummaries(
            entries("Prologue" to "A man walks into the mists.", "Chapter 1" to "Vin.", "Chapter 2" to "Kelsier."),
            chapters,
        )

        assertEquals("A man walks into the mists.", match.byChapterIndex[0])
        assertEquals("Vin.", match.byChapterIndex[1])
        assertEquals("Kelsier.", match.byChapterIndex[2])
    }

    @Test
    fun `a chapter number written another way still matches`() {
        val match = matchSummaries(
            entries("Chapter 9" to "The ninth."),
            mapOf(0 to "Chapter IX"),
        )

        assertEquals("The ninth.", match.byChapterIndex[0])
    }

    @Test
    fun `duplicate labels are consumed in order`() {
        val match = matchSummaries(
            entries("Chapter 1" to "First book.", "Chapter 1" to "Second book."),
            mapOf(0 to "Chapter 1", 5 to "Chapter 1"),
        )

        assertEquals("First book.", match.byChapterIndex[0])
        assertEquals("Second book.", match.byChapterIndex[5])
    }

    @Test
    fun `entries matching nothing are counted but not stored`() {
        val match = matchSummaries(
            entries("Chapter 1" to "Vin.", "Chapter 7" to "Not in this book."),
            mapOf(0 to "Chapter 1", 1 to "Chapter 2"),
        )

        assertEquals(1, match.matchedCount)
        assertEquals(2, match.entryCount)
        assertEquals(2, match.chapterCount)
        assertEquals(setOf(0), match.byChapterIndex.keys)
    }

    @Test
    fun `a book with one unrecognizable chapter matches nothing`() {
        // The chapterless rips: one span covering the whole file, titled after it.
        val match = matchSummaries(
            entries("Chapter 1" to "Vin.", "Chapter 2" to "Kelsier."),
            mapOf(0 to "The Final Empire"),
        )

        assertEquals(0, match.matchedCount)
        assertEquals(1, match.chapterCount)
    }

    @Test
    fun `nothing is paired by position when labels do not match`() {
        // The absent fallback. Two entries, two chapters, same order, and still no pairing --
        // because a positional guess here is wrong silently.
        val match = matchSummaries(
            entries("Chapter 1" to "Vin.", "Chapter 2" to "Kelsier."),
            mapOf(0 to "Chapter 8", 1 to "Chapter 9"),
        )

        assertEquals(0, match.matchedCount)
    }

    @Test
    fun `chapter titles are matched rather than chapter indices`() {
        // Guards the mistake ChapterMatching documents: an index stringifies into a valid chapter
        // number. Chapter 0 is titled "Prologue", so an entry for chapter 1 must not land on it.
        val match = matchSummaries(
            entries("Chapter 1" to "Vin."),
            mapOf(0 to "Prologue", 1 to "Chapter 1"),
        )

        assertNull(match.byChapterIndex[0])
        assertEquals("Vin.", match.byChapterIndex[1])
    }
}
