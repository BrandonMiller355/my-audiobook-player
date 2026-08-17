package com.brandonmiller.audiobookplayer.ebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The D2 subset, stated as tests so "known gap" stays distinguishable from "bug". */
class XhtmlBlocksTest {

    @Test
    fun `paragraphs and headings become their own blocks`() {
        val blocks = blocks("<h1>Title</h1><p>One</p><p>Two</p>")

        assertEquals(listOf(BlockKind.Heading, BlockKind.Paragraph, BlockKind.Paragraph), blocks.map { it.kind })
        assertEquals(listOf("Title", "One", "Two"), blocks.map { it.text })
        assertEquals(1, blocks.first().headingLevel)
    }

    @Test
    fun `heading levels are carried through`() {
        assertEquals(listOf(2, 3), blocks("<h2>A</h2><h3>B</h3>").map { it.headingLevel })
    }

    @Test
    fun `whitespace inside a block is collapsed to single spaces`() {
        // The owner's books hard-wrap mid-sentence in the source; keeping it would break prose at
        // the wrong points on every paragraph.
        val blocks = blocks("<p>had not been\n   born a\t\twarrior</p>")

        assertEquals("had not been born a warrior", blocks.single().text)
    }

    @Test
    fun `a br becomes a line break within its block`() {
        assertEquals("One\nTwo", blocks("<p>One<br/>Two</p>").single().text)
    }

    @Test
    fun `an hr becomes a rule block with no text`() {
        val blocks = blocks("<p>Before</p><hr/><p>After</p>")

        assertEquals(listOf(BlockKind.Paragraph, BlockKind.Rule, BlockKind.Paragraph), blocks.map { it.kind })
        assertEquals("", blocks[1].text)
    }

    @Test
    fun `block quotes and list items keep their kind`() {
        val blocks = blocks("<blockquote>Quoted</blockquote><ul><li>Item</li></ul>")

        assertEquals(listOf(BlockKind.Quote, BlockKind.ListItem), blocks.map { it.kind })
    }

    @Test
    fun `ordered list items are numbered and unordered ones are not`() {
        val ordered = blocks("<ol><li>A</li><li>B</li><li>C</li></ol>")
        assertEquals(listOf(1, 2, 3), ordered.map { it.listOrdinal })

        val unordered = blocks("<ul><li>A</li><li>B</li></ul>")
        assertTrue(unordered.all { it.listOrdinal == null })
    }

    // ------------------------------------------------------------------ emphasis

    @Test
    fun `emphasis marks the run it covers`() {
        val block = blocks("<p>plain <em>italic</em> plain</p>").single()

        val span = block.emphasis.single()
        assertEquals(setOf(Emphasis.Italic), span.styles)
        assertEquals("italic", block.text.substring(span.start, span.end))
    }

    @Test
    fun `nested emphasis accumulates rather than replacing`() {
        // `<strong><b>3</b></strong>` occurs in the owner's books.
        val block = blocks("<p><strong><b>3</b></strong></p>").single()

        assertEquals(setOf(Emphasis.Bold), block.emphasis.single().styles)
    }

    @Test
    fun `distinct nested styles combine on the same run`() {
        val block = blocks("<p><em><strong>both</strong></em></p>").single()

        assertEquals(setOf(Emphasis.Italic, Emphasis.Bold), block.emphasis.single().styles)
    }

    @Test
    fun `adjacent runs with the same styles merge`() {
        val block = blocks("<p><em>one<span>two</span>three</em></p>").single()

        assertEquals(1, block.emphasis.size)
        assertEquals("onetwothree", block.text.substring(block.emphasis[0].start, block.emphasis[0].end))
    }

    @Test
    fun `underline is part of the subset`() {
        assertEquals(setOf(Emphasis.Underline), blocks("<p><u>x</u></p>").single().emphasis.single().styles)
    }

    @Test
    fun `a stray closing tag does not disable later emphasis`() {
        val block = blocks("<p></em><em>italic</em></p>").single()

        assertEquals(setOf(Emphasis.Italic), block.emphasis.single().styles)
    }

    // ------------------------------------------------------------------ outside the subset

    @Test
    fun `links contribute their text without their formatting`() {
        assertEquals("see the appendix", blocks("""<p>see <a href="x.html">the appendix</a></p>""").single().text)
    }

    @Test
    fun `a table becomes one block per row rather than a grid`() {
        val blocks = blocks("<table><tr><td>Iron</td><td>Pulling</td></tr><tr><td>Steel</td><td>Pushing</td></tr></table>")

        assertEquals(listOf("Iron Pulling", "Steel Pushing"), blocks.map { it.text })
    }

    @Test
    fun `scripts and styles contribute nothing`() {
        val blocks = blocks("<style>p { color: red }</style><script>var x = 1;</script><p>Only this</p>")

        assertEquals(listOf("Only this"), blocks.map { it.text })
    }

    @Test
    fun `the head is skipped entirely`() {
        val parsed = XhtmlBlocks.parse(
            "<html><head><title>Not content</title></head><body><p>Content</p></body></html>",
            spineIndex = 0,
        )

        assertEquals(listOf("Content"), parsed.blocks.map { it.text })
    }

    @Test
    fun `an image contributes nothing but does not break its paragraph`() {
        assertEquals("before after", blocks("""<p>before <img src="x.jpg"/> after</p>""").single().text)
    }

    @Test
    fun `empty blocks are dropped`() {
        assertEquals(listOf("Real"), blocks("<p></p><p>   </p><div></div><p>Real</p>").map { it.text })
    }

    // ------------------------------------------------------------------ entities

    @Test
    fun `named and numeric entities are decoded`() {
        assertEquals("a&b<c—d…e", blocks("<p>a&amp;b&lt;c&mdash;d&hellip;e</p>").single().text)
        assertEquals("AB", blocks("<p>&#65;&#x42;</p>").single().text)
    }

    @Test
    fun `a non-breaking space is decoded and then collapsed like any other space`() {
        assertEquals("a b", blocks("<p>a&nbsp;b</p>").single().text)
    }

    @Test
    fun `an unknown entity is left verbatim rather than lost`() {
        assertEquals("&sigma; x", blocks("<p>&sigma; x</p>").single().text)
    }

    @Test
    fun `a bare ampersand in prose survives`() {
        assertEquals("Tom & Jerry", blocks("<p>Tom & Jerry</p>").single().text)
    }

    // ------------------------------------------------------------------ anchors

    @Test
    fun `an id maps to the block carrying it`() {
        val parsed = XhtmlBlocks.parse(
            "<body><p>One</p><h2 id=\"target\">Two</h2><p>Three</p></body>",
            spineIndex = 0,
        )

        assertEquals(1, parsed.anchors["target"])
    }

    @Test
    fun `a legacy anchor name maps to the block containing it`() {
        val parsed = XhtmlBlocks.parse(
            "<body><h2><a name=\"_Toc123\"></a>Heading</h2></body>",
            spineIndex = 0,
        )

        assertEquals(0, parsed.anchors["_Toc123"])
    }

    @Test
    fun `several anchors on one heading all resolve to it`() {
        val parsed = XhtmlBlocks.parse(
            "<body><p>Before</p><h2 id=\"a\"><a name=\"b\"></a><a name=\"c\"></a>Heading</h2></body>",
            spineIndex = 0,
        )

        assertEquals(listOf(1, 1, 1), listOf("a", "b", "c").map { parsed.anchors[it] })
    }

    private fun blocks(inner: String): List<Block> =
        XhtmlBlocks.parse("<body>$inner</body>", spineIndex = 0).blocks
}
