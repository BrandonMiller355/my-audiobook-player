package com.brandonmiller.audiobookplayer.ebook

/**
 * Inline styling a run of text carries. Combinable — `<strong><b>3</b></strong>` occurs in real
 * books and yields a single run holding both, rather than two nested runs (design D2).
 */
enum class Emphasis { Italic, Bold, Underline }

/** A styled run within a [Block]'s text, in character offsets from the start of that block. */
data class EmphasisSpan(val start: Int, val end: Int, val styles: Set<Emphasis>)

/**
 * What a block is, which is what decides how it is drawn. Deliberately coarse: the reader renders
 * prose, and a distinction it cannot draw differently is a distinction not worth carrying.
 */
enum class BlockKind { Paragraph, Heading, Quote, ListItem, Rule }

/**
 * One rendered unit — a paragraph, a heading, a list item, a rule.
 *
 * Blocks are paragraphs rather than whole documents on purpose: an EPUB that puts an entire book in
 * one XHTML file still produces many blocks, so the `LazyColumn` stays lazy and no single
 * `AnnotatedString` becomes enormous (design R3).
 *
 * [spineIndex] and [charOffset] are what the saved reading position anchors to (design D3).
 * [charOffset] counts characters of block text preceding this one *within the same spine item*, so
 * it stays meaningful when the surrounding book changes and survives every font and layout change.
 */
data class Block(
    val kind: BlockKind,
    val text: String,
    val spineIndex: Int,
    val charOffset: Int,
    val emphasis: List<EmphasisSpan> = emptyList(),
    /** 1–6 for [BlockKind.Heading], 0 otherwise. */
    val headingLevel: Int = 0,
    /** The number to show for an ordered list item, or null for an unordered one. */
    val listOrdinal: Int? = null,
)

/**
 * One table-of-contents entry.
 *
 * [depth] is retained rather than flattened because the owner's books nest chapters under parts,
 * and a table of contents that lists "PART ONE" and "1" at the same level reads as noise
 * (design spike finding 2).
 */
data class NavEntry(val label: String, val depth: Int, val blockIndex: Int)

/** Where the user was reading. Resolved against [Ebook.blockIndexFor] to a scroll target. */
data class ReadingPosition(val spineIndex: Int, val charOffset: Int) {
    companion object {
        val START = ReadingPosition(spineIndex = 0, charOffset = 0)
    }
}

/** A parsed book: the whole text as blocks, plus its own table of contents. */
data class Ebook(
    val title: String,
    val blocks: List<Block>,
    val contents: List<NavEntry>,
) {

    /**
     * The block containing [position], or the nearest one after it.
     *
     * Scrolling lands on the containing block's start rather than mid-block, which is what makes a
     * few characters of drift harmless if text extraction ever changes slightly (design R2).
     */
    fun blockIndexFor(position: ReadingPosition): Int {
        if (blocks.isEmpty()) return 0

        // Blocks are ordered by spine item and then by offset within it, so the last block at or
        // before the target is the one holding it.
        var candidate = -1
        for (i in blocks.indices) {
            val block = blocks[i]
            val atOrBefore = block.spineIndex < position.spineIndex ||
                (block.spineIndex == position.spineIndex && block.charOffset <= position.charOffset)
            if (atOrBefore) candidate = i else break
        }
        return candidate.coerceAtLeast(0)
    }

    /** The position to save for a reader currently showing [blockIndex]. */
    fun positionOf(blockIndex: Int): ReadingPosition {
        val block = blocks.getOrNull(blockIndex) ?: return ReadingPosition.START
        return ReadingPosition(block.spineIndex, block.charOffset)
    }
}

/**
 * The outcome of reading an EPUB.
 *
 * [Encrypted] is separate from [NotAnEpub] because it is the one failure the app refuses on purpose
 * rather than fails at — DRM is a PRD §3 non-goal (design D12) — and the message the user sees
 * should say so rather than implying the file is broken.
 */
sealed interface EbookParseResult {

    data class Parsed(val book: Ebook) : EbookParseResult

    /** Carries `META-INF/encryption.xml`. Refused, never worked around. */
    data object Encrypted : EbookParseResult

    /** Readable as a file, but not an EPUB — the wrong type entirely, or missing its structure. */
    data class NotAnEpub(val reason: String) : EbookParseResult

    /** The bytes could not be read at all: unreadable stream, corrupt archive, gone file. */
    data class Unreadable(val reason: String) : EbookParseResult
}
