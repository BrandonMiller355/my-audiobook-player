package com.brandonmiller.audiobookplayer.ebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class EbookCharacterPrefixSumsTest {

    @Test
    fun `each block's prefix sum is the total length of every block before it`() {
        val book = bookOf("abc", "de", "fghi")

        assertEquals(listOf(0, 3, 5), book.characterPrefixSums.toList())
        assertEquals(9, book.totalCharacters)
    }

    @Test
    fun `absoluteCharsAt and blockIndexForAbsoluteChars invert each other at block starts`() {
        val book = bookOf("abc", "de", "fghi")

        for (index in book.blocks.indices) {
            assertEquals(index, book.blockIndexForAbsoluteChars(book.absoluteCharsAt(index)))
        }
    }

    @Test
    fun `blockIndexForAbsoluteChars resolves a mid-block position to the block containing it`() {
        val book = bookOf("abc", "de", "fghi")

        assertEquals(1, book.blockIndexForAbsoluteChars(4)) // "de" starts at 3, runs to 5
    }

    @Test
    fun `blockIndexForAbsoluteChars clamps past either end`() {
        val book = bookOf("abc", "de")

        assertEquals(0, book.blockIndexForAbsoluteChars(-5))
        assertEquals(1, book.blockIndexForAbsoluteChars(999))
    }

    @Test
    fun `an empty book has no prefix sums and resolves to block zero`() {
        val book = Ebook(title = "Empty", blocks = emptyList(), contents = emptyList())

        assertTrue(book.characterPrefixSums.isEmpty())
        assertEquals(0, book.totalCharacters)
        assertEquals(0, book.blockIndexForAbsoluteChars(100))
    }

    @Test
    fun `computing prefix sums for a real-sized book is negligible`() {
        // Roughly the ~900,000-character book PRD §23 sizes this against, as 4,500 paragraphs of
        // 200 characters — comparable to a real chapter/paragraph split.
        val book = bookOf(*Array(4_500) { "x".repeat(200) })

        val elapsed = measureTimeMillis { book.characterPrefixSums }

        assertTrue("expected well under a second, took ${elapsed}ms", elapsed < 200)
    }

    /**
     * The property the glide rests on (design D6, D7): the target has to keep moving *within* a
     * block. Quantizing to the block start is what made the page sit still through a paragraph and
     * then lurch, so advancing one character must advance the answer.
     */
    @Test
    fun `the fractional position advances within a block rather than only between blocks`() {
        val book = bookOf("a".repeat(100), "b".repeat(100))

        val quarter = book.textPositionForAbsoluteChars(125)
        val half = book.textPositionForAbsoluteChars(150)

        assertEquals(1, quarter.blockIndex)
        assertEquals(1, half.blockIndex)
        assertEquals(0.25f, quarter.fraction, 0.001f)
        assertEquals(0.5f, half.fraction, 0.001f)
        assertTrue("must be strictly increasing inside one block", half.fraction > quarter.fraction)
    }

    @Test
    fun `the fractional position lands on block boundaries exactly`() {
        val book = bookOf("a".repeat(100), "b".repeat(100))

        assertEquals(TextPosition(0, 0f), book.textPositionForAbsoluteChars(0))
        assertEquals(TextPosition(1, 0f), book.textPositionForAbsoluteChars(100))
    }

    /** A rule or an empty paragraph has no inside, so there is no fraction to be partway through. */
    @Test
    fun `a zero-length block reports no fraction rather than dividing by zero`() {
        val book = bookOf("a".repeat(50), "", "b".repeat(50))

        val position = book.textPositionForAbsoluteChars(50)

        assertEquals(0f, position.fraction, 0.001f)
    }

    @Test
    fun `positions past the end clamp rather than running off`() {
        val book = bookOf("a".repeat(100))

        val position = book.textPositionForAbsoluteChars(10_000)

        assertEquals(0, position.blockIndex)
        assertEquals(1f, position.fraction, 0.001f)
    }

    private fun bookOf(vararg texts: String) = Ebook(
        title = "Book",
        blocks = texts.mapIndexed { index, text ->
            Block(kind = BlockKind.Paragraph, text = text, spineIndex = 0, charOffset = index * 1000)
        },
        contents = emptyList(),
    )
}
