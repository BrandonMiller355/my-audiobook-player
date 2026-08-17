package com.brandonmiller.audiobookplayer.ebook

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads an EPUB into the [Ebook] the reader draws.
 *
 * An EPUB is a ZIP of XHTML, so this needs no dependency beyond `java.util.zip` and the scanner in
 * [Markup] — the same answer the project already reached for MP4 chapter atoms (design D1).
 *
 * The whole book is parsed at open rather than a document at a time (design D11). A 150,000-word
 * novel is roughly a megabyte of text, so holding all of it makes continuous scrolling actually
 * continuous, makes a table-of-contents jump an index lookup, and makes restoring a saved position
 * a search rather than a load.
 *
 * Never throws. Every failure comes back as an [EbookParseResult], because a malformed file must
 * not be able to crash the app (PRD §22).
 */
object EpubParser {

    private const val MIMETYPE_ENTRY = "mimetype"
    private const val CONTAINER_ENTRY = "META-INF/container.xml"
    private const val ENCRYPTION_ENTRY = "META-INF/encryption.xml"

    const val EPUB_MIMETYPE = "application/epub+zip"

    /** Guards against a hostile or absurd archive; far above any real book's text. */
    private const val MAX_TOTAL_TEXT_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024

    /** Only these are worth holding in memory. Images and fonts are not rendered (design D2). */
    private val TEXT_EXTENSIONS = setOf("xhtml", "html", "htm", "xml", "opf", "ncx", "txt")

    fun parse(source: InputStream): EbookParseResult {
        val entries = try {
            readTextEntries(source)
        } catch (e: Exception) {
            // Broad on purpose: a truncated archive, a closed stream, and a file that is not a ZIP
            // at all all surface differently, and none of them may reach the caller.
            return EbookParseResult.Unreadable(
                "could not read the file (${e.javaClass.simpleName}: ${e.message})",
            )
        }

        if (entries.sawEncryption) return EbookParseResult.Encrypted
        if (entries.files.isEmpty()) return EbookParseResult.NotAnEpub("the file contains nothing readable")
        if (!entries.declaresEpubMimetype) {
            // Only a hint: some producers omit or misplace `mimetype`, so a missing container is
            // what actually decides. Checked below.
            if (entries.text(CONTAINER_ENTRY) == null) {
                return EbookParseResult.NotAnEpub("the file is not an EPUB")
            }
        }

        val containerXml = entries.text(CONTAINER_ENTRY)
            ?: return EbookParseResult.NotAnEpub("the file has no EPUB container")
        val opfPath = EpubPackage.readOpfPath(containerXml)
            ?: return EbookParseResult.NotAnEpub("the EPUB container names no package document")
        val opfXml = entries.text(opfPath)
            ?: return EbookParseResult.NotAnEpub("the EPUB's package document is missing")

        val pkg = EpubPackage.readPackage(opfXml, opfPath)
        if (pkg.spine.isEmpty()) return EbookParseResult.NotAnEpub("the EPUB has no readable contents")

        return EbookParseResult.Parsed(assemble(pkg, opfXml, opfPath, entries))
    }

    private fun assemble(
        pkg: EpubPackage.Package,
        opfXml: String,
        opfPath: String,
        entries: Entries,
    ): Ebook {
        val blocks = mutableListOf<Block>()

        // Where each spine document's blocks begin, and every anchor within it, so a
        // table-of-contents fragment resolves to a block rather than to the top of a file.
        val documentStart = mutableMapOf<String, Int>()
        val anchors = mutableMapOf<String, Int>()

        pkg.spine.forEachIndexed { spineIndex, item ->
            val xhtml = entries.text(item.path) ?: return@forEachIndexed
            val parsed = XhtmlBlocks.parse(xhtml, spineIndex)
            if (parsed.blocks.isEmpty()) return@forEachIndexed

            val base = blocks.size
            documentStart.putIfAbsent(item.path, base)
            parsed.anchors.forEach { (id, index) -> anchors.putIfAbsent("${item.path}#$id", base + index) }
            blocks += parsed.blocks
        }

        return Ebook(
            title = pkg.title,
            blocks = blocks,
            contents = readContents(pkg, opfXml, opfPath, entries)
                .mapNotNull { it.resolve(documentStart, anchors) },
        )
    }

    /** EPUB 3 navigation when present, the EPUB 2 NCX otherwise (spike finding 1). */
    private fun readContents(
        pkg: EpubPackage.Package,
        opfXml: String,
        opfPath: String,
        entries: Entries,
    ): List<EpubPackage.NavTarget> {
        EpubPackage.readNavPath(opfXml, opfPath)?.let { navPath ->
            entries.text(navPath)?.let { nav ->
                val targets = EpubPackage.readNav(nav, navPath)
                if (targets.isNotEmpty()) return targets
            }
        }
        val tocPath = pkg.tocPath ?: return emptyList()
        val ncx = entries.text(tocPath) ?: return emptyList()
        return EpubPackage.readNcx(ncx, tocPath)
    }

    private fun EpubPackage.NavTarget.resolve(
        documentStart: Map<String, Int>,
        anchors: Map<String, Int>,
    ): NavEntry? {
        val blockIndex = fragment?.let { anchors["$path#$it"] }
            ?: documentStart[path]
            ?: return null
        return NavEntry(label = label, depth = depth, blockIndex = blockIndex)
    }

    // ---------------------------------------------------------------- archive

    private class Entries(
        val files: Map<String, String>,
        val sawEncryption: Boolean,
        val declaresEpubMimetype: Boolean,
    ) {
        /**
         * Looks a path up tolerantly. Archive entry names and the hrefs pointing at them disagree
         * about percent-encoding and occasionally about case, and a book that fails to open over a
         * `%20` is a book that looks broken for no reason the user can act on.
         */
        fun text(path: String): String? {
            files[path]?.let { return it }
            val decoded = EpubPackage.percentDecode(path)
            files[decoded]?.let { return it }
            val lower = decoded.lowercase()
            return files.entries.firstOrNull { it.key.lowercase() == lower }?.value
        }
    }

    /**
     * One sequential pass over the archive, holding every text entry.
     *
     * A single pass rather than random access because the source is a SAF stream, which is not
     * seekable, and because the entry that says which entries matter — the OPF — can appear after
     * the documents it names.
     */
    private fun readTextEntries(source: InputStream): Entries {
        val files = mutableMapOf<String, String>()
        var sawEncryption = false
        var declaresEpubMimetype = false
        var total = 0L

        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')

                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (name == ENCRYPTION_ENTRY) {
                    sawEncryption = true
                    zip.closeEntry()
                    continue
                }

                val extension = name.substringAfterLast('.', "").lowercase()
                val wanted = name == MIMETYPE_ENTRY || extension in TEXT_EXTENSIONS
                if (!wanted || total >= MAX_TOTAL_TEXT_BYTES) {
                    zip.closeEntry()
                    continue
                }

                val bytes = readEntry(zip)
                total += bytes.size
                zip.closeEntry()

                if (name == MIMETYPE_ENTRY) {
                    declaresEpubMimetype = String(bytes, Charsets.UTF_8).trim() == EPUB_MIMETYPE
                } else {
                    files[name] = decodeText(bytes)
                }
            }
        }

        return Entries(files, sawEncryption, declaresEpubMimetype)
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < MAX_ENTRY_BYTES) {
            val read = zip.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Decodes as UTF-8 unless a byte-order mark says otherwise.
     *
     * Undecodable bytes become replacement characters rather than an error: a mangled character is
     * better than a failed book, which is the same call [com.brandonmiller.audiobookplayer.m4b.M4bChapterParser]
     * makes for chapter titles.
     */
    private fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        // Charsets.UTF_16 consumes the mark itself, which UTF_16LE/BE would leave as a
        // zero-width character at the head of the document.
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, Charsets.UTF_16)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, Charsets.UTF_16)
        else -> String(bytes, Charsets.UTF_8)
    }
}
