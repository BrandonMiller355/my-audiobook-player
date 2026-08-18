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

    private fun bookOf(vararg texts: String) = Ebook(
        title = "Book",
        blocks = texts.mapIndexed { index, text ->
            Block(kind = BlockKind.Paragraph, text = text, spineIndex = 0, charOffset = index * 1000)
        },
        contents = emptyList(),
    )
}
