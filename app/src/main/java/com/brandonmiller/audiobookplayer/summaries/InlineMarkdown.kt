package com.brandonmiller.audiobookplayer.summaries

import com.brandonmiller.audiobookplayer.ebook.Emphasis
import com.brandonmiller.audiobookplayer.ebook.EmphasisSpan

/**
 * Plain text and the emphasis over it, read out of Markdown's inline syntax
 * (`add-chapter-summaries` design D12).
 *
 * Produces the same [EmphasisSpan] shape the EPUB reader already builds from XHTML tags, so the
 * summary sheet styles its text through the same path `ReaderScreen.annotate` uses rather than a
 * second one. Nothing here knows about Compose, which is what keeps it unit-testable.
 */
data class InlineText(val text: String, val emphasis: List<EmphasisSpan>)

/** Marks recognized as emphasis, longest first so `**` is tried before `*`. */
private val DELIMITERS = listOf(
    "**" to Emphasis.Bold,
    "__" to Emphasis.Bold,
    "*" to Emphasis.Italic,
    "_" to Emphasis.Italic,
)

/**
 * Strips Markdown's inline marks and reports what they emphasized.
 *
 * A mark only opens a span when a matching close exists later on the same line, and closing marks
 * are matched to the nearest open one. An unpaired mark is kept as an ordinary character — prose
 * about a 5 * 3 grid, or a file that opens a bold run and never closes it, reads as what it says
 * rather than losing a character or swallowing the rest of the summary.
 *
 * Backticks are removed without styling: this sheet has one text style, so a code span has nothing
 * to render as, and leaving the backticks in would put punctuation on screen that the file's author
 * meant as markup.
 *
 * Deliberately not a Markdown parser. Summaries are prose; block syntax beyond the headings
 * [classifySummaryLine] already handles does not appear in them, and guessing at link syntax or
 * nested emphasis would cost more than it returns.
 */
fun inlineMarkdown(source: String): InlineText {
    val out = StringBuilder()
    val spans = mutableListOf<EmphasisSpan>()
    // Delimiter, the style it opens, and where the emphasized run began in `out`.
    val open = ArrayDeque<Triple<String, Emphasis, Int>>()

    var i = 0
    while (i < source.length) {
        if (source[i] == '`') {
            i++
            continue
        }

        val delimiter = DELIMITERS.firstOrNull { source.startsWith(it.first, i) }
        if (delimiter == null) {
            out.append(source[i])
            i++
            continue
        }

        val (mark, style) = delimiter
        val openIndex = open.indexOfLast { it.first == mark }
        when {
            openIndex >= 0 -> {
                // Close it, and drop anything opened inside that never closed.
                val (_, openStyle, start) = open.elementAt(openIndex)
                repeat(open.size - openIndex) { open.removeLast() }
                if (out.length > start) spans += EmphasisSpan(start, out.length, setOf(openStyle))
            }
            // Only open when this mark closes somewhere ahead; otherwise it is ordinary text.
            source.indexOf(mark, i + mark.length) >= 0 -> open.addLast(Triple(mark, style, out.length))
            else -> {
                out.append(mark)
            }
        }
        i += mark.length
    }

    return InlineText(out.toString(), spans.sortedBy { it.start })
}
