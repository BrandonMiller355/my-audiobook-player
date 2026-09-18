package com.brandonmiller.audiobookplayer.summaries

import com.brandonmiller.audiobookplayer.playback.ChapterKey
import com.brandonmiller.audiobookplayer.playback.NAMED_SECTIONS
import com.brandonmiller.audiobookplayer.playback.normalizeChapterLabel

/**
 * Reading a summary file and pairing what it contains with a book's chapters (`add-chapter-summaries`
 * design D3, D4).
 *
 * Both halves live in one file because neither is used without the other: an import parses, matches,
 * and stores in one pass, and splitting them would mean two files of sixty lines that are only ever
 * read together.
 *
 * Nothing here touches the file system or the database. The whole import is a pure function of the
 * file's text and the book's stored chapter titles, which is what makes the awkward cases —
 * a prologue occupying chapter one, Roman numerals, a file numbered from the wrong end — testable
 * without a device.
 */

/** One entry read out of a summary file: the label from its marker line, and the prose beneath it. */
data class SummaryEntry(val label: String, val text: String)

/**
 * What an import produced. [byChapterIndex] is what gets stored; the two counts are what the chapter
 * sheet reports, and they are the reason the matcher returns a type rather than a bare map — "42"
 * means nothing without the "of 90" beside it (design D8).
 */
data class SummaryMatch(
    val byChapterIndex: Map<Int, String>,
    /** How many entries the file yielded, matched or not. */
    val entryCount: Int,
    /** How many chapters the book has, matched or not. */
    val chapterCount: Int,
) {
    val matchedCount: Int get() = byChapterIndex.size
}

// ------------------------------------------------------------------ parsing

/** What may follow a chapter reference and still leave the line a marker rather than prose. */
private val TITLE_SEPARATORS = charArrayOf(':', '—', '–')

/**
 * A Markdown heading: up to three spaces of indent, one to six hashes, then the text, with any
 * closing run of hashes discarded.
 */
private val ATX_HEADING = Regex("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")

/** Inline emphasis and code, removed from a *label* before it is read as a chapter reference. */
private val LABEL_EMPHASIS = Regex("[*_`]")

/**
 * What a line is, once read (`add-chapter-summaries` design D3, revised for Markdown).
 *
 * [StructuralHeading] is the case a plain-text file never needed. A Markdown summary file has
 * headings that are not chapters — the document's own title, and a "Part One" above the chapters
 * belonging to it — and they are neither markers nor prose. Treating them as prose would append the
 * document title to whatever entry preceded it; treating them as markers would create entries that
 * match nothing. Ending the current entry and discarding what follows until the next real marker is
 * what they actually mean.
 */
internal sealed interface SummaryLine {
    data class Marker(val label: String) : SummaryLine
    data object StructuralHeading : SummaryLine
    data object Body : SummaryLine
}

/**
 * Reads one line as a marker, a structural heading, or prose.
 *
 * A heading may carry a title and is trusted to be a heading — it is never prose, whatever it says.
 * A plain line has to earn it, under the length rule [chapterReferenceIn] applies, because in a
 * plain-text file the delimiter and the prose are the same kind of thing.
 */
internal fun classifySummaryLine(line: String): SummaryLine {
    val heading = ATX_HEADING.matchEntire(line.removeSuffix("\r"))
    if (heading != null) {
        val label = chapterReferenceIn(heading.groupValues[2].replace(LABEL_EMPHASIS, "").trim())
        return if (label == null) SummaryLine.StructuralHeading else SummaryLine.Marker(label)
    }
    val label = chapterReferenceIn(line.trim())
    return if (label == null) SummaryLine.Body else SummaryLine.Marker(label)
}

/**
 * A marker's own words, past which a line is prose. Three matches the window `normalizeChapterLabel`
 * allows itself for an unprefixed label, and admits "Chapter Twenty One" and "Interlude 2".
 */
private const val MAX_MARKER_WORDS = 3

/**
 * The label this line is a marker for, or null if it is ordinary text.
 *
 * A line is a marker when the part of it before any title separator is, on its own, a chapter
 * reference — `Chapter 12`, `Chapter XII`, `Chapter Twelve`, `Prologue` — and is short enough to be
 * nothing else. Both conditions are load-bearing, and together they are the whole defense of a
 * plain-text format whose delimiter is also ordinary prose (design D3):
 *
 * - A summary beginning "Chapter 4 was where the heist turned" carries no separator, so the whole
 *   line has to pass, and at seven words it is over [MAX_MARKER_WORDS]. Note that
 *   `normalizeChapterLabel` alone would read it as chapter four quite happily — the length test, not
 *   the normalizer, is what rejects it.
 * - "Chapter 4 was where the heist turned: Vin knew..." carries a separator, and the part before it
 *   is still seven words.
 *
 * Accepting a title after the separator is a deliberate widening of what the specification's
 * scenarios require. "Chapter 1: The Well of Ascension" is what an assistant emits by default when
 * asked for chapter summaries, and refusing it would fail a whole file for a reason the owner would
 * have to guess at.
 */
internal fun chapterReferenceIn(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    val separatorAt = trimmed.indexOfFirst { it in TITLE_SEPARATORS }
    val head = if (separatorAt >= 0) trimmed.take(separatorAt) else trimmed
    val label = head.trim().trimEnd('.', ',', '-', ' ')
    if (label.isEmpty()) return null
    if (label.split(' ').count { it.isNotEmpty() } > MAX_MARKER_WORDS) return null
    if (!namesAChapter(label)) return null

    return if (normalizeChapterLabel(label) is ChapterKey.Unrecognized) null else label
}

/**
 * Whether [label] is shaped like a chapter reference at all, before asking what it denotes.
 *
 * `normalizeChapterLabel` cannot answer this on its own, and must not be asked to: it exists to read
 * whatever an EPUB or a container happens to call a chapter, so it is deliberately generous, and
 * over a three-word window it will find a number almost anywhere. Handed "Mistborn Book 3" it
 * returns chapter three quite reasonably — that is the right answer for a table-of-contents entry
 * and the wrong one for the title line of a summary file, which is exactly what
 * "# Mistborn Book 3: The Hero of Ages — Chapter Summaries" is. Left ungated, a document's own title
 * silently becomes chapter three's summary, and the front matter beneath it becomes that chapter's
 * text.
 *
 * So a label has to look like a reference before it is read as one: it says "chapter", or it names a
 * section, or it is a single token standing alone — `12`, `IX`. A phrase that merely contains a
 * number is a title.
 */
private fun namesAChapter(label: String): Boolean {
    val words = label.lowercase().replace(NON_ALPHANUMERIC, " ").trim()
        .split(' ').filter { it.isNotEmpty() }

    return when {
        words.isEmpty() -> false
        "chapter" in words -> true
        words.any { it in NAMED_SECTIONS } -> true
        else -> words.size == 1
    }
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/**
 * Splits a summary file into its entries, in the order they appear.
 *
 * Text before the first marker line is discarded, which is what absorbs an assistant's "Here are the
 * chapter summaries you asked for." An entry whose body is blank is dropped rather than stored: an
 * empty summary would put a control on a chapter row that opens nothing.
 */
fun parseSummaryFile(text: String): List<SummaryEntry> {
    val entries = mutableListOf<SummaryEntry>()
    val body = StringBuilder()
    var label: String? = null

    fun flush() {
        val current = label ?: return
        val summary = body.toString().trim()
        if (summary.isNotEmpty()) entries += SummaryEntry(current, summary)
        body.setLength(0)
    }

    // removeSuffix rather than a split on a line-ending regex: a file written on the desktop arrives
    // with CRLF, and a stray carriage return left on the end of a marker line would survive the
    // length test and then fail to normalize.
    for (raw in text.lineSequence()) {
        val line = raw.removeSuffix("\r")
        when (val kind = classifySummaryLine(line)) {
            is SummaryLine.Marker -> {
                flush()
                label = kind.label
            }
            // Ends whatever entry was open and starts nothing, so a document title or a "Part One"
            // heading neither joins the summary above it nor becomes an entry of its own.
            SummaryLine.StructuralHeading -> {
                flush()
                label = null
            }
            SummaryLine.Body -> if (label != null) body.appendLine(line)
        }
    }
    flush()

    return entries
}

// ------------------------------------------------------------------ matching

/**
 * Pairs a file's entries with a book's chapters by what each label denotes (design D4).
 *
 * [chapterTitles] maps `ChapterEntity.chapterIndex` to `ChapterEntity.title` — the *stored* titles,
 * never anything derived from an index. A stringified index normalizes into a perfectly valid chapter
 * number and would pair every entry with a plausible, wrong chapter; `audioChaptersFrom` in
 * `ChapterMatching` exists to prevent the same mistake on the read-along side, and the failure looks
 * like ordinary drift rather than like a bug.
 *
 * Where a key appears more than once — an omnibus with two chapter ones — entries and chapters are
 * consumed in order, first against first, as `matchByLabel` does.
 *
 * There is deliberately no fallback to pairing in order. `matchChapters` has one, and it earns its
 * place there: read-along's alternative is the feature not working at all, and `detectOffset` scores
 * candidate alignments against characters per millisecond, a physical quantity that exposes a wrong
 * offset. Nothing here offers an equivalent signal, so a positional guess would give every chapter a
 * confidently wrong summary — a failure indistinguishable, from the outside, from the feature working.
 * Matching nothing and saying so is the better outcome.
 */
fun matchSummaries(entries: List<SummaryEntry>, chapterTitles: Map<Int, String>): SummaryMatch {
    val chaptersByKey = mutableMapOf<ChapterKey, MutableList<Int>>()
    for ((index, title) in chapterTitles.entries.sortedBy { it.key }) {
        val key = normalizeChapterLabel(title)
        if (key is ChapterKey.Unrecognized) continue
        chaptersByKey.getOrPut(key) { mutableListOf() } += index
    }

    val matched = linkedMapOf<Int, String>()
    for (entry in entries) {
        val key = normalizeChapterLabel(entry.label)
        if (key is ChapterKey.Unrecognized) continue
        val candidates = chaptersByKey[key] ?: continue
        if (candidates.isEmpty()) continue
        matched[candidates.removeAt(0)] = entry.text
    }

    return SummaryMatch(
        byChapterIndex = matched,
        entryCount = entries.size,
        chapterCount = chapterTitles.size,
    )
}
