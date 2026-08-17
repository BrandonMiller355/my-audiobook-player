package com.brandonmiller.audiobookplayer.ebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fixtures are built here rather than checked in as binaries, the same call
 * [com.brandonmiller.audiobookplayer.m4b.M4bChapterParserTest] makes: synthetic archives can
 * express the encrypted, reversed-manifest, and malformed-markup cases that a real book never
 * would, and the one real book available is 730 KB of someone else's copyright.
 *
 * Plain JUnit, no Robolectric — the parser is deliberately free of Android types (design D17).
 */
class EpubParserTest {

    // ------------------------------------------------------------------ structure

    @Test
    fun `a minimal epub yields its spine in reading order`() {
        val epub = epub(
            spine = listOf(
                "a.xhtml" to body("<p>First</p>"),
                "b.xhtml" to body("<p>Second</p>"),
            ),
        )

        val book = parsed(epub)

        assertEquals(listOf("First", "Second"), book.blocks.map { it.text })
        assertEquals(listOf(0, 1), book.blocks.map { it.spineIndex })
    }

    @Test
    fun `reading order follows the spine, not manifest id order`() {
        // The owner's books number ids descending against ascending filenames, so ordering by id
        // renders the book backwards — convincingly enough to survive a quick look.
        val epub = epub(
            spine = listOf(
                "first.xhtml" to body("<p>First</p>"),
                "second.xhtml" to body("<p>Second</p>"),
                "third.xhtml" to body("<p>Third</p>"),
            ),
            idFor = { index -> "html${9 - index}" },
        )

        val book = parsed(epub)

        assertEquals(listOf("First", "Second", "Third"), book.blocks.map { it.text })
    }

    @Test
    fun `the package title is read`() {
        assertEquals("The Hero of Ages", parsed(epub(title = "The Hero of Ages")).title)
    }

    @Test
    fun `an opf in a subdirectory resolves its hrefs relative to itself`() {
        val epub = epub(
            opfPath = "OEBPS/content.opf",
            spine = listOf("text/a.xhtml" to body("<p>Nested</p>")),
        )

        assertEquals(listOf("Nested"), parsed(epub).blocks.map { it.text })
    }

    @Test
    fun `percent-encoded hrefs resolve to their archive entries`() {
        val epub = buildEpub(
            opfPath = "content.opf",
            manifest = listOf(Triple("c1", "a%20b.xhtml", "application/xhtml+xml")),
            spineIds = listOf("c1"),
            files = mapOf("a b.xhtml" to body("<p>Spaced</p>")),
        )

        assertEquals(listOf("Spaced"), parsed(epub).blocks.map { it.text })
    }

    // ------------------------------------------------------------------ refusals

    @Test
    fun `an encrypted epub is refused rather than rendered`() {
        val epub = epub(
            spine = listOf("a.xhtml" to body("<p>Hidden</p>")),
            extra = mapOf("META-INF/encryption.xml" to "<encryption/>"),
        )

        assertEquals(EbookParseResult.Encrypted, EpubParser.parse(ByteArrayInputStream(epub)))
    }

    @Test
    fun `a file that is not a zip is unreadable`() {
        val result = EpubParser.parse(ByteArrayInputStream("this is not a zip".toByteArray()))

        assertTrue(result.toString(), result is EbookParseResult.NotAnEpub || result is EbookParseResult.Unreadable)
    }

    @Test
    fun `a zip that is not an epub is rejected`() {
        val zip = zip(mapOf("readme.txt" to "nothing to see"))

        assertTrue(EpubParser.parse(ByteArrayInputStream(zip)) is EbookParseResult.NotAnEpub)
    }

    @Test
    fun `an epub with no spine is rejected`() {
        val epub = buildEpub(
            opfPath = "content.opf",
            manifest = emptyList(),
            spineIds = emptyList(),
            files = emptyMap(),
        )

        assertTrue(EpubParser.parse(ByteArrayInputStream(epub)) is EbookParseResult.NotAnEpub)
    }

    // ------------------------------------------------------------------ table of contents

    @Test
    fun `a nested ncx keeps its depth and lands on the right blocks`() {
        val epub = epub(
            spine = listOf(
                "part.xhtml" to body("<h1 id=\"p1\">PART ONE</h1>"),
                "one.xhtml" to body("<h2 id=\"c1\">1</h2><p>Text</p>"),
            ),
            ncx = """
                <navMap>
                  <navPoint><navLabel><text>PART ONE</text></navLabel>
                    <content src="part.xhtml#p1"/>
                    <navPoint><navLabel><text>1</text></navLabel>
                      <content src="one.xhtml#c1"/>
                    </navPoint>
                  </navPoint>
                </navMap>
            """.trimIndent(),
        )

        val contents = parsed(epub).contents

        assertEquals(listOf("PART ONE", "1"), contents.map { it.label })
        assertEquals(listOf(0, 1), contents.map { it.depth })
        assertEquals(listOf(0, 1), contents.map { it.blockIndex })
    }

    @Test
    fun `an ncx fragment targets the block carrying that anchor`() {
        val epub = epub(
            spine = listOf(
                "one.xhtml" to body("<p>Before</p><h2 id=\"mid\">Middle</h2><p>After</p>"),
            ),
            ncx = """
                <navMap>
                  <navPoint><navLabel><text>Middle</text></navLabel>
                    <content src="one.xhtml#mid"/>
                  </navPoint>
                </navMap>
            """.trimIndent(),
        )

        assertEquals(1, parsed(epub).contents.single().blockIndex)
    }

    @Test
    fun `an ncx entry with no fragment targets the start of its document`() {
        val epub = epub(
            spine = listOf(
                "one.xhtml" to body("<p>One</p>"),
                "two.xhtml" to body("<p>Two</p>"),
            ),
            ncx = """
                <navMap>
                  <navPoint><navLabel><text>Two</text></navLabel>
                    <content src="two.xhtml"/>
                  </navPoint>
                </navMap>
            """.trimIndent(),
        )

        assertEquals(1, parsed(epub).contents.single().blockIndex)
    }

    @Test
    fun `an epub 3 navigation document is read and takes precedence`() {
        val epub = epub(
            spine = listOf(
                "one.xhtml" to body("<p>One</p>"),
                "two.xhtml" to body("<p>Two</p>"),
            ),
            nav = """
                <nav epub:type="toc">
                  <ol>
                    <li><a href="one.xhtml">Chapter One</a>
                      <ol><li><a href="two.xhtml">Chapter Two</a></li></ol>
                    </li>
                  </ol>
                </nav>
            """.trimIndent(),
            ncx = "<navMap><navPoint><navLabel><text>Stale</text></navLabel>" +
                "<content src=\"one.xhtml\"/></navPoint></navMap>",
        )

        val contents = parsed(epub).contents

        assertEquals(listOf("Chapter One", "Chapter Two"), contents.map { it.label })
        assertEquals(listOf(0, 1), contents.map { it.depth })
    }

    @Test
    fun `an epub with no navigation has empty contents and still reads`() {
        val book = parsed(epub(spine = listOf("a.xhtml" to body("<p>Alone</p>"))))

        assertTrue(book.contents.isEmpty())
        assertEquals(listOf("Alone"), book.blocks.map { it.text })
    }

    // ------------------------------------------------------------------ resilience

    @Test
    fun `malformed markup renders what it can`() {
        val epub = epub(
            spine = listOf(
                "a.xhtml" to "<html><body><p>Unclosed<br><p>Next</em></p></body>",
            ),
        )

        assertEquals(listOf("Unclosed", "Next"), parsed(epub).blocks.map { it.text })
    }

    @Test
    fun `a single-document book still yields many blocks`() {
        // Some EPUBs put the whole book in one file; blocks are paragraphs, so laziness survives.
        val paragraphs = (1..500).joinToString("") { "<p>Paragraph $it</p>" }
        val epub = epub(spine = listOf("all.xhtml" to body(paragraphs)))

        val book = parsed(epub)

        assertEquals(500, book.blocks.size)
        assertTrue(book.blocks.all { it.spineIndex == 0 })
    }

    @Test
    fun `a spine document missing from the archive is skipped rather than fatal`() {
        val epub = buildEpub(
            opfPath = "content.opf",
            manifest = listOf(
                Triple("c1", "there.xhtml", "application/xhtml+xml"),
                Triple("c2", "gone.xhtml", "application/xhtml+xml"),
            ),
            spineIds = listOf("c1", "c2"),
            files = mapOf("there.xhtml" to body("<p>Here</p>")),
        )

        assertEquals(listOf("Here"), parsed(epub).blocks.map { it.text })
    }

    // ------------------------------------------------------------------ positions

    @Test
    fun `character offsets accumulate within a spine item and restart at the next`() {
        val epub = epub(
            spine = listOf(
                "a.xhtml" to body("<p>12345</p><p>678</p>"),
                "b.xhtml" to body("<p>90</p>"),
            ),
        )

        val blocks = parsed(epub).blocks

        assertEquals(listOf(0, 5, 0), blocks.map { it.charOffset })
        assertEquals(listOf(0, 0, 1), blocks.map { it.spineIndex })
    }

    @Test
    fun `a position resolves to the block containing it`() {
        val book = parsed(
            epub(spine = listOf("a.xhtml" to body("<p>12345</p><p>678</p><p>9</p>"))),
        )

        assertEquals(0, book.blockIndexFor(ReadingPosition(0, 0)))
        assertEquals(0, book.blockIndexFor(ReadingPosition(0, 3)))
        assertEquals(1, book.blockIndexFor(ReadingPosition(0, 5)))
        assertEquals(2, book.blockIndexFor(ReadingPosition(0, 8)))
    }

    @Test
    fun `a position past the end of the book resolves to its last block`() {
        val book = parsed(epub(spine = listOf("a.xhtml" to body("<p>One</p><p>Two</p>"))))

        assertEquals(1, book.blockIndexFor(ReadingPosition(9, 9999)))
    }

    @Test
    fun `positions round-trip through the block they came from`() {
        val book = parsed(
            epub(
                spine = listOf(
                    "a.xhtml" to body("<p>First</p><p>Second</p>"),
                    "b.xhtml" to body("<p>Third</p>"),
                ),
            ),
        )

        book.blocks.indices.forEach { index ->
            assertEquals(index, book.blockIndexFor(book.positionOf(index)))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun parsed(epub: ByteArray): Ebook {
        val result = EpubParser.parse(ByteArrayInputStream(epub))
        assertTrue("expected a parsed book, got $result", result is EbookParseResult.Parsed)
        return (result as EbookParseResult.Parsed).book
    }

    private fun body(inner: String) = "<html><head><title>ignored</title></head><body>$inner</body></html>"

    /** Builds a well-formed EPUB around [spine], adding whichever navigation forms were asked for. */
    private fun epub(
        spine: List<Pair<String, String>> = listOf("a.xhtml" to body("<p>Text</p>")),
        title: String = "Test Book",
        opfPath: String = "content.opf",
        ncx: String? = null,
        nav: String? = null,
        extra: Map<String, String> = emptyMap(),
        idFor: (Int) -> String = { "c$it" },
    ): ByteArray {
        val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = spine.mapIndexed { index, (href, _) ->
            Triple(idFor(index), href, "application/xhtml+xml")
        }.toMutableList()
        val files = spine.associate { (href, content) -> opfDir + href to content }.toMutableMap()

        if (ncx != null) {
            manifest += Triple("ncx", "toc.ncx", "application/x-dtbncx+xml")
            files[opfDir + "toc.ncx"] = "<ncx>$ncx</ncx>"
        }
        if (nav != null) {
            manifest += Triple("navdoc", "nav.xhtml", "application/xhtml+xml")
            files[opfDir + "nav.xhtml"] = body(nav)
        }

        return buildEpub(
            opfPath = opfPath,
            manifest = manifest,
            spineIds = spine.indices.map(idFor),
            files = files + extra,
            title = title,
            tocId = if (ncx != null) "ncx" else null,
            navId = if (nav != null) "navdoc" else null,
        )
    }

    /** The archive itself, so tests can build shapes [epub] deliberately cannot. */
    private fun buildEpub(
        opfPath: String,
        manifest: List<Triple<String, String, String>>,
        spineIds: List<String>,
        files: Map<String, String>,
        title: String = "Test Book",
        tocId: String? = null,
        navId: String? = null,
    ): ByteArray {
        val items = manifest.joinToString("\n") { (id, href, type) ->
            val properties = if (id == navId) " properties=\"nav\"" else ""
            """<item id="$id" href="$href" media-type="$type"$properties/>"""
        }
        val itemrefs = spineIds.joinToString("\n") { """<itemref idref="$it"/>""" }
        val spineAttr = tocId?.let { " toc=\"$it\"" }.orEmpty()

        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>$title</dc:title>
              </metadata>
              <manifest>
            $items
              </manifest>
              <spine$spineAttr>
            $itemrefs
              </spine>
            </package>
        """.trimIndent()

        val container = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()

        return zip(
            mapOf(
                "mimetype" to EpubParser.EPUB_MIMETYPE,
                "META-INF/container.xml" to container,
                opfPath to opf,
            ) + files,
        )
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `the fixture builder produces something the parser accepts`() {
        // Guards the helpers themselves: a broken builder would make every test above vacuous.
        assertNotNull(parsed(epub()))
        assertNull((EpubParser.parse(ByteArrayInputStream(epub())) as? EbookParseResult.NotAnEpub))
    }
}
