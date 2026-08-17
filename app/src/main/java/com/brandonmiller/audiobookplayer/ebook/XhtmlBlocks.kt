package com.brandonmiller.audiobookplayer.ebook

/**
 * Turns one spine document's XHTML into the ordered [Block]s the reader draws.
 *
 * Implements exactly the subset in design D2. Anything outside it contributes its text without its
 * formatting — a table becomes one line per row rather than a grid, a link becomes plain text —
 * because losing the words is worse than losing their appearance.
 */
internal object XhtmlBlocks {

    /** Elements that end the block being built and begin a new one. */
    private val BLOCK_ELEMENTS = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "li", "div", "section", "article", "main",
        "tr", "table", "pre", "figcaption", "dt", "dd", "center",
    )

    /** Elements whose entire subtree carries nothing the reader shows. */
    private val SKIPPED_ELEMENTS = setOf("head", "script", "style", "svg")

    /**
     * Cells stay within their row's block but need separating, or `<td>Iron</td><td>Pulling</td>`
     * renders as "IronPulling" — the difference between a table degrading to readable text and
     * degrading to a mess.
     */
    private val CELL_ELEMENTS = setOf("td", "th")

    private val HEADING_LEVELS = mapOf(
        "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6,
    )

    private val ITALIC_ELEMENTS = setOf("em", "i", "cite", "var", "dfn")
    private val BOLD_ELEMENTS = setOf("strong", "b")
    private val UNDERLINE_ELEMENTS = setOf("u", "ins")

    /** What one document yielded: its blocks, and where its anchors landed among them. */
    data class Parsed(
        val blocks: List<Block>,
        /** Fragment id to index within [blocks], for table-of-contents targets (D3, finding 3). */
        val anchors: Map<String, Int>,
    )

    fun parse(xhtml: String, spineIndex: Int): Parsed = Builder(spineIndex).run {
        for (token in Markup.tokenize(xhtml)) consume(token)
        finish()
    }

    /**
     * Walks the token stream holding the state a block needs: the text so far, which emphasis is
     * open, how deep in lists we are, and which anchors have been seen but not yet placed.
     */
    private class Builder(private val spineIndex: Int) {

        private val blocks = mutableListOf<Block>()
        private val anchors = mutableMapOf<String, Int>()

        private val text = StringBuilder()
        private val spans = mutableListOf<EmphasisSpan>()

        private var kind = BlockKind.Paragraph
        private var headingLevel = 0
        private var listOrdinal: Int? = null

        /** Characters of block text already emitted for this spine item — the next block's offset. */
        private var charOffset = 0

        /** Nesting counts, so `<em>a<em>b</em>c</em>` keeps `c` italic. */
        private val emphasisDepth = mutableMapOf<Emphasis, Int>()

        /** Set while inside a skipped subtree; cleared by that element's end tag. */
        private var skipUntil: String? = null

        /** One entry per open list: its ordinal counter, or null when the list is unordered. */
        private val listStack = mutableListOf<Int?>()

        /** Anchors seen since the last block was emitted, waiting for the block they belong to. */
        private val pendingAnchors = mutableListOf<String>()

        fun consume(token: Markup.Token) {
            skipUntil?.let { skipped ->
                if (token is Markup.Token.End && token.name == skipped) skipUntil = null
                return
            }

            when (token) {
                is Markup.Token.Start -> onStart(token)
                is Markup.Token.End -> onEnd(token.name)
                is Markup.Token.Text -> appendText(token.value)
            }
        }

        private fun onStart(token: Markup.Token.Start) {
            val name = token.name

            if (name in SKIPPED_ELEMENTS) {
                // Self-closing skipped elements have no subtree to skip.
                if (!token.selfClosing) skipUntil = name
                return
            }

            // `id` on any element and the legacy `<a name>` are both table-of-contents targets;
            // the owner's books use both, sometimes on the same heading.
            token.attributes["id"]?.takeIf { it.isNotEmpty() }?.let { pendingAnchors += it }
            if (name == "a") token.attributes["name"]?.takeIf { it.isNotEmpty() }?.let { pendingAnchors += it }

            when {
                name == "br" -> if (text.isNotEmpty()) text.append('\n')
                name == "hr" -> {
                    flush()
                    kind = BlockKind.Rule
                    flush()
                }
                name == "ol" -> listStack += 1
                name == "ul" -> listStack += null
                name in BLOCK_ELEMENTS -> beginBlock(name)
                name in ITALIC_ELEMENTS -> open(Emphasis.Italic)
                name in BOLD_ELEMENTS -> open(Emphasis.Bold)
                name in UNDERLINE_ELEMENTS -> open(Emphasis.Underline)
            }
        }

        private fun onEnd(name: String) {
            when {
                name == "ol" || name == "ul" -> listStack.removeLastOrNull()
                name in CELL_ELEMENTS -> appendText(" ")
                name in BLOCK_ELEMENTS -> flush()
                name in ITALIC_ELEMENTS -> close(Emphasis.Italic)
                name in BOLD_ELEMENTS -> close(Emphasis.Bold)
                name in UNDERLINE_ELEMENTS -> close(Emphasis.Underline)
            }
        }

        private fun beginBlock(name: String) {
            flush()
            kind = when {
                name in HEADING_LEVELS -> BlockKind.Heading
                name == "blockquote" -> BlockKind.Quote
                name == "li" -> BlockKind.ListItem
                else -> BlockKind.Paragraph
            }
            headingLevel = HEADING_LEVELS[name] ?: 0
            listOrdinal = if (name == "li") nextListOrdinal() else null
        }

        /** Advances and returns the innermost ordered list's counter, or null inside a `<ul>`. */
        private fun nextListOrdinal(): Int? {
            val index = listStack.lastIndex
            if (index < 0) return null
            val current = listStack[index] ?: return null
            listStack[index] = current + 1
            return current
        }

        private fun open(style: Emphasis) {
            emphasisDepth[style] = (emphasisDepth[style] ?: 0) + 1
        }

        private fun close(style: Emphasis) {
            val depth = emphasisDepth[style] ?: return
            // A stray close tag with no open must not push the count negative — later text would
            // then never regain the style.
            if (depth <= 1) emphasisDepth.remove(style) else emphasisDepth[style] = depth - 1
        }

        /**
         * Appends text with whitespace collapsed to single spaces.
         *
         * Not cosmetic: the owner's EPUBs hard-wrap mid-sentence in the source, so preserving it
         * would break every paragraph at the wrong points (design D2, spike finding 5).
         */
        private fun appendText(raw: String) {
            if (raw.isEmpty()) return

            val start = text.length
            for (c in raw) {
                if (c.isWhitespace()) {
                    // Never open a block with a space, and never repeat one.
                    val last = text.lastOrNull()
                    if (last != null && last != ' ' && last != '\n') text.append(' ')
                } else {
                    text.append(c)
                }
            }
            if (text.length == start) return

            val styles = emphasisDepth.keys.toSet()
            if (styles.isNotEmpty()) spans += EmphasisSpan(start, text.length, styles)
        }

        /** Emits the block being built, if it holds anything, and resets for the next. */
        private fun flush() {
            val trimmedStart = text.indexOfFirst { !it.isWhitespace() }
            val body = if (trimmedStart < 0) "" else text.toString().trimEnd()

            if (body.isNotEmpty() || kind == BlockKind.Rule) {
                blocks += Block(
                    kind = kind,
                    text = body,
                    spineIndex = spineIndex,
                    charOffset = charOffset,
                    emphasis = mergeSpans(spans, body.length),
                    headingLevel = headingLevel,
                    listOrdinal = listOrdinal,
                )
                charOffset += body.length
                pendingAnchors.forEach { anchors[it] = blocks.lastIndex }
                pendingAnchors.clear()
            }

            text.setLength(0)
            spans.clear()
            kind = BlockKind.Paragraph
            headingLevel = 0
            listOrdinal = null
        }

        fun finish(): Parsed {
            flush()
            // Anchors after the final block — a trailing `<a name>` — point at the end of the
            // document, which is the last block rather than nowhere.
            if (pendingAnchors.isNotEmpty() && blocks.isNotEmpty()) {
                pendingAnchors.forEach { anchors[it] = blocks.lastIndex }
            }
            return Parsed(blocks, anchors)
        }

        /**
         * Joins runs that carry the same styles and clips them to the trimmed text.
         *
         * Emphasis arrives one text token at a time, so an italic sentence broken across tokens by
         * a nested tag would otherwise become several adjacent identical spans.
         */
        private fun mergeSpans(raw: List<EmphasisSpan>, limit: Int): List<EmphasisSpan> {
            if (raw.isEmpty()) return emptyList()

            val merged = mutableListOf<EmphasisSpan>()
            for (span in raw) {
                val clipped = EmphasisSpan(
                    start = span.start.coerceIn(0, limit),
                    end = span.end.coerceIn(0, limit),
                    styles = span.styles,
                )
                if (clipped.end <= clipped.start) continue

                val last = merged.lastOrNull()
                if (last != null && last.styles == clipped.styles && last.end >= clipped.start) {
                    merged[merged.lastIndex] = last.copy(end = maxOf(last.end, clipped.end))
                } else {
                    merged += clipped
                }
            }
            return merged
        }
    }
}
