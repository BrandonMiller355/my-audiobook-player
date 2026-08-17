package com.brandonmiller.audiobookplayer.ebook

/**
 * Reads an EPUB's structural documents: the container, the OPF package, and whichever form of
 * table of contents the book carries.
 *
 * These three are well-formed XML by specification, unlike the content documents, but they are read
 * with the same lenient scanner — a second parsing path earns its keep only if it does something
 * the first cannot (design D17).
 */
internal object EpubPackage {

    /** A spine entry: where the document lives in the archive, in reading order. */
    data class SpineItem(val path: String)

    data class Package(val title: String, val spine: List<SpineItem>, val tocPath: String?)

    /** A table-of-contents entry before its target has been resolved to a block. */
    data class NavTarget(val label: String, val depth: Int, val path: String, val fragment: String?)

    // ---------------------------------------------------------------- container

    /**
     * The OPF's path, from `META-INF/container.xml`.
     *
     * A book may declare several rootfiles; the first is the one to render, per the specification.
     */
    fun readOpfPath(containerXml: String): String? =
        Markup.tokenize(containerXml)
            .filterIsInstance<Markup.Token.Start>()
            .firstOrNull { it.name == "rootfile" }
            ?.attributes?.get("full-path")
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizePath(it) }

    // ---------------------------------------------------------------- OPF

    /**
     * The package document: title, reading order, and where the table of contents lives.
     *
     * Reading order comes from the spine's sequence with each `idref` resolved through the manifest.
     * This matters more than it looks: the owner's books number their manifest ids *descending*
     * against ascending filenames, so anything that orders by id renders the book backwards, and
     * convincingly enough to survive a quick look (spike finding 4).
     */
    fun readPackage(opfXml: String, opfPath: String): Package {
        val base = directoryOf(opfPath)
        val tokens = Markup.tokenize(opfXml)

        val manifest = mutableMapOf<String, String>()
        val manifestMediaTypes = mutableMapOf<String, String>()
        val spineIdrefs = mutableListOf<String>()
        var tocId: String? = null
        var title = ""

        var inMetadata = false
        var capturingTitle = false

        for (token in tokens) {
            when (token) {
                is Markup.Token.Start -> when (token.name) {
                    "metadata" -> inMetadata = true
                    "item" -> {
                        val id = token.attributes["id"]
                        val href = token.attributes["href"]
                        if (!id.isNullOrEmpty() && !href.isNullOrEmpty()) {
                            manifest[id] = resolvePath(base, href)
                            manifestMediaTypes[id] = token.attributes["media-type"].orEmpty()
                        }
                    }
                    "spine" -> tocId = token.attributes["toc"]?.takeIf { it.isNotEmpty() }
                    "itemref" -> token.attributes["idref"]
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { spineIdrefs += it }
                    // Only the package title; a content document's `<title>` never reaches here.
                    "title" -> if (inMetadata && title.isEmpty()) capturingTitle = true
                }

                is Markup.Token.End -> when (token.name) {
                    "metadata" -> inMetadata = false
                    "title" -> capturingTitle = false
                }

                is Markup.Token.Text -> if (capturingTitle) title += token.value
            }
        }

        val spine = spineIdrefs.mapNotNull { manifest[it] }.map(::SpineItem)

        return Package(
            title = title.trim(),
            spine = spine,
            tocPath = resolveTocPath(tocId, manifest, manifestMediaTypes),
        )
    }

    /**
     * Where to look for a table of contents.
     *
     * The EPUB 3 navigation document is marked `properties="nav"`; the EPUB 2 NCX is named by the
     * spine's `toc` attribute. The owner's library has no EPUB 3 `nav` at all, so the NCX path is
     * the one that actually runs (spike finding 1), but a book carrying both is a book whose `nav`
     * is the better source.
     */
    private fun resolveTocPath(
        tocId: String?,
        manifest: Map<String, String>,
        mediaTypes: Map<String, String>,
    ): String? {
        tocId?.let { manifest[it] }?.let { return it }
        return manifest.entries
            .firstOrNull { mediaTypes[it.key]?.contains("ncx") == true }
            ?.value
    }

    /** The `properties="nav"` document, if the book has one. Read from the raw OPF for simplicity. */
    fun readNavPath(opfXml: String, opfPath: String): String? {
        val base = directoryOf(opfPath)
        return Markup.tokenize(opfXml)
            .filterIsInstance<Markup.Token.Start>()
            .firstOrNull { it.name == "item" && it.attributes["properties"]?.contains("nav") == true }
            ?.attributes?.get("href")
            ?.takeIf { it.isNotEmpty() }
            ?.let { resolvePath(base, it) }
    }

    // ---------------------------------------------------------------- table of contents

    /**
     * An EPUB 2 NCX, keeping `navPoint` nesting as depth.
     *
     * Entries are created when their `navPoint` opens and filled in as the label and target arrive,
     * which is what keeps parents ahead of their children in the result — a parent's `<content>`
     * comes before its child `navPoint`s, but its label comes before that again.
     */
    fun readNcx(ncxXml: String, ncxPath: String): List<NavTarget> {
        val base = directoryOf(ncxPath)
        val ordered = mutableListOf<MutableTarget>()
        val open = mutableListOf<MutableTarget>()
        var inNavLabel = false

        for (token in Markup.tokenize(ncxXml)) {
            when (token) {
                is Markup.Token.Start -> when (token.name) {
                    "navpoint" -> {
                        val target = MutableTarget(depth = open.size)
                        ordered += target
                        open += target
                    }
                    "navlabel" -> inNavLabel = true
                    "content" -> token.attributes["src"]
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { src -> open.lastOrNull()?.let { if (it.src == null) it.src = src } }
                }

                is Markup.Token.End -> when (token.name) {
                    "navpoint" -> open.removeLastOrNull()
                    "navlabel" -> inNavLabel = false
                }

                is Markup.Token.Text ->
                    if (inNavLabel) open.lastOrNull()?.let { it.label += token.value }
            }
        }

        return ordered.mapNotNull { it.toTarget(base) }
    }

    /**
     * An EPUB 3 navigation document. Depth comes from `<ol>` nesting, which is how the format
     * expresses the same grouping the NCX expresses with nested `navPoint`s.
     */
    fun readNav(navXhtml: String, navPath: String): List<NavTarget> {
        val base = directoryOf(navPath)
        val targets = mutableListOf<NavTarget>()

        var listDepth = 0
        var capturing: StringBuilder? = null
        var capturingHref: String? = null

        for (token in Markup.tokenize(navXhtml)) {
            when (token) {
                is Markup.Token.Start -> when (token.name) {
                    "ol", "ul" -> listDepth++
                    "a" -> token.attributes["href"]?.takeIf { it.isNotEmpty() }?.let {
                        capturingHref = it
                        capturing = StringBuilder()
                    }
                }

                is Markup.Token.End -> when (token.name) {
                    "ol", "ul" -> if (listDepth > 0) listDepth--
                    "a" -> {
                        val label = capturing?.toString()?.trim().orEmpty()
                        val href = capturingHref
                        if (href != null && label.isNotEmpty()) {
                            targets += target(label, (listDepth - 1).coerceAtLeast(0), base, href)
                        }
                        capturing = null
                        capturingHref = null
                    }
                }

                is Markup.Token.Text -> capturing?.append(token.value)
            }
        }
        return targets
    }

    private class MutableTarget(val depth: Int) {
        var label: String = ""
        var src: String? = null

        fun toTarget(base: String): NavTarget? {
            val text = label.trim()
            val source = src
            if (text.isEmpty() || source.isNullOrEmpty()) return null
            return target(text, depth, base, source)
        }
    }

    private fun target(label: String, depth: Int, base: String, href: String): NavTarget {
        val fragment = href.substringAfter('#', "").takeIf { it.isNotEmpty() }
        return NavTarget(
            label = label,
            depth = depth,
            path = resolvePath(base, href.substringBefore('#')),
            fragment = fragment,
        )
    }

    // ---------------------------------------------------------------- paths

    private fun directoryOf(path: String): String =
        path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

    /**
     * Resolves an href against the document that contained it, collapsing `.` and `..`.
     *
     * Hrefs may be percent-encoded even when the archive's own entry names are not — producers
     * disagree — so this decodes before resolving and the caller matches against decoded names.
     */
    fun resolvePath(base: String, href: String): String {
        val decoded = percentDecode(href.trim())
        if (decoded.startsWith('/')) return normalizePath(decoded.removePrefix("/"))
        return normalizePath(base + decoded)
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    /** Decodes `%XX` escapes as UTF-8, leaving anything malformed exactly as it was found. */
    fun percentDecode(raw: String): String {
        if (!raw.contains('%')) return raw

        val bytes = java.io.ByteArrayOutputStream(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%' && i + 2 < raw.length) {
                val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                    continue
                }
            }
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
