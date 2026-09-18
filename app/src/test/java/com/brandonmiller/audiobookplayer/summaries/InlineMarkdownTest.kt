package com.brandonmiller.audiobookplayer.summaries

import com.brandonmiller.audiobookplayer.ebook.Emphasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineMarkdownTest {

    @Test
    fun `bold is removed from the text and reported as a span`() {
        val result = inlineMarkdown("The entity is revealed to be **Ruin**, the being Vin released.")

        assertEquals("The entity is revealed to be Ruin, the being Vin released.", result.text)
        val span = result.emphasis.single()
        assertEquals(setOf(Emphasis.Bold), span.styles)
        assertEquals("Ruin", result.text.substring(span.start, span.end))
    }

    @Test
    fun `italic is recognized in both spellings`() {
        for (source in listOf("a *word* here", "a _word_ here")) {
            val result = inlineMarkdown(source)
            assertEquals("a word here", result.text)
            assertEquals(setOf(Emphasis.Italic), result.emphasis.single().styles)
            assertEquals("word", result.text.substring(result.emphasis.single().start, result.emphasis.single().end))
        }
    }

    @Test
    fun `a bold label at the start of a line keeps its text`() {
        val result = inlineMarkdown("**Opening epigraph:** An unidentified narrator claims to be the Hero.")

        assertEquals("Opening epigraph: An unidentified narrator claims to be the Hero.", result.text)
        assertEquals("Opening epigraph:", result.text.substring(result.emphasis.single().start, result.emphasis.single().end))
    }

    @Test
    fun `several spans in one paragraph are all reported`() {
        val result = inlineMarkdown("**Ruin** and **Hemalurgy** both matter.")

        assertEquals("Ruin and Hemalurgy both matter.", result.text)
        assertEquals(2, result.emphasis.size)
        assertEquals("Ruin", result.text.substring(result.emphasis[0].start, result.emphasis[0].end))
        assertEquals("Hemalurgy", result.text.substring(result.emphasis[1].start, result.emphasis[1].end))
    }

    @Test
    fun `bold is preferred over italic where both could match`() {
        val result = inlineMarkdown("**bold**")

        assertEquals("bold", result.text)
        assertEquals(setOf(Emphasis.Bold), result.emphasis.single().styles)
    }

    @Test
    fun `an unpaired mark stays in the text`() {
        // Prose, not markup. Losing the character or swallowing the rest of the summary would both
        // be worse than showing what the file says.
        assertEquals("a 5 * 3 grid", inlineMarkdown("a 5 * 3 grid").text)
        assertTrue(inlineMarkdown("a 5 * 3 grid").emphasis.isEmpty())
        assertEquals("an **unclosed run", inlineMarkdown("an **unclosed run").text)
    }

    @Test
    fun `backticks are removed without styling`() {
        val result = inlineMarkdown("the `Book3` folder")

        assertEquals("the Book3 folder", result.text)
        assertTrue(result.emphasis.isEmpty())
    }

    @Test
    fun `text with no markup is returned unchanged`() {
        val source = "Marsh is trapped inside his own body, able to think independently."

        assertEquals(source, inlineMarkdown(source).text)
        assertTrue(inlineMarkdown(source).emphasis.isEmpty())
    }

    @Test
    fun `an empty emphasis run produces no span`() {
        assertEquals("", inlineMarkdown("****").text)
        assertTrue(inlineMarkdown("****").emphasis.isEmpty())
    }

    @Test
    fun `every span lands inside the text it describes`() {
        val result = inlineMarkdown("**Ruin** freed *the* mists, and `code` too, plus an * orphan.")

        result.emphasis.forEach { span ->
            assertTrue("span starts inside the text", span.start in 0..result.text.length)
            assertTrue("span ends inside the text", span.end in span.start..result.text.length)
        }
    }
}
