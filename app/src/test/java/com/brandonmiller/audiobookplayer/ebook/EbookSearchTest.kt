package com.brandonmiller.audiobookplayer.ebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EbookSearchTest {

    @Test
    fun `matching is case-insensitive and returns blocks in reading order`() {
        val book = bookOf("Vin walked", "nothing here", "vin waited")

        assertEquals(listOf(0, 2), book.search("VIN").map { it.blockIndex })
    }

    @Test
    fun `a query shorter than the minimum finds nothing`() {
        val book = bookOf("Vin walked")

        assertTrue(book.search("V").isEmpty())
        assertTrue(book.search(" ").isEmpty())
    }

    @Test
    fun `the match range points at the query within the snippet`() {
        val hit = bookOf("Vin walked the mists").search("mists").single()

        assertEquals("mists", hit.snippet.substring(hit.matchStart, hit.matchEnd))
    }

    @Test
    fun `a long block is cut down to a snippet around the match`() {
        val block = "a".repeat(400) + " needle " + "b".repeat(400)
        val hit = bookOf(block).search("needle").single()

        assertTrue(hit.snippet.length < 200)
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
        assertEquals("needle", hit.snippet.substring(hit.matchStart, hit.matchEnd))
    }

    @Test
    fun `a block matching twice yields one hit, since both scroll to the same place`() {
        assertEquals(1, bookOf("mist and mist").search("mist").size)
    }

    @Test
    fun `the hit count is capped`() {
        val book = bookOf(*Array(Ebook.MAX_SEARCH_HITS + 20) { "mist" })

        assertEquals(Ebook.MAX_SEARCH_HITS, book.search("mist").size)
    }

    private fun bookOf(vararg texts: String) = Ebook(
        title = "Book",
        blocks = texts.mapIndexed { index, text ->
            Block(kind = BlockKind.Paragraph, text = text, spineIndex = 0, charOffset = index * 100)
        },
        contents = emptyList(),
    )
}
