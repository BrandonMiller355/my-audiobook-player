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

/**
 * One entry read out of a summary file: the label from its marker line, and the prose beneath it.
 *
 * [section] is the book or part the entry sat under — "2" beneath `## Book II`, "epilogue" beneath
 * `## Epilogue` — for the novels that restart their chapter numbers in every book. Null when the file
 * names none.
 */
data class SummaryEntry(val label: String, val text: String, val section: String? = null)

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
    val heading = headingIn(line)
    if (heading != null) {
        val label = chapterReferenceIn(heading.text)
        return if (label == null) SummaryLine.StructuralHeading else SummaryLine.Marker(label)
    }
    val label = chapterReferenceIn(line.trim())
    if (label != null) return SummaryLine.Marker(label)
    // A plain "Book II" line is the plain-text spelling of a section heading. Left as prose it would
    // join the last summary of the book before it.
    return if (sectionIn(line) != null) SummaryLine.StructuralHeading else SummaryLine.Body
}

/** A Markdown heading's level and its text, emphasis removed; null for any other line. */
private data class Heading(val level: Int, val text: String)

private fun headingIn(line: String): Heading? {
    val match = ATX_HEADING.matchEntire(line.removeSuffix("\r")) ?: return null
    return Heading(match.groupValues[1].length, match.groupValues[2].replace(LABEL_EMPHASIS, "").trim())
}

/** Words that open a section heading in a novel published in parts: `Book II`, `Part One`. */
private val SECTION_WORDS = setOf("book", "part", "volume")

/**
 * The section a heading opens — "2" for `Book II: An Unfortunate Gathering` — or null.
 *
 * The section word has to come first. "Mistborn Book 3: The Hero of Ages — Chapter Summaries" is a
 * document title that happens to contain a book number, and reading it as a section would only be
 * harmless by luck; the same gate [namesAChapter] applies for the same reason.
 */
internal fun sectionIn(text: String): String? {
    val trimmed = text.trim()
    val separatorAt = trimmed.indexOfFirst { it in TITLE_SEPARATORS }
    val head = if (separatorAt >= 0) trimmed.take(separatorAt) else trimmed
    val words = wordsOf(head)
    if (words.size !in 2..MAX_MARKER_WORDS || words[0] !in SECTION_WORDS) return null
    return sectionNumber(words.drop(1))
}

private fun sectionNumber(words: List<String>): String? =
    (normalizeChapterLabel(words.take(2).joinToString(" ")) as? ChapterKey.Numbered)?.ordinal?.toString()

private fun wordsOf(text: String): List<String> =
    text.lowercase().replace(NON_ALPHANUMERIC, " ").trim().split(' ').filter { it.isNotEmpty() }

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
    // "Book 2, Chapter 1" is a reference, not prose: the section in front of it does not count
    // against the length rule.
    val words = wordsOf(label)
    val chapterAt = words.indexOf("chapter")
    val counted = if (chapterAt > 0 && sectionNumber(words.take(chapterAt).drop(1)) != null &&
        words[0] in SECTION_WORDS
    ) {
        words.size - chapterAt
    } else {
        label.split(' ').count { it.isNotEmpty() }
    }
    if (counted > MAX_MARKER_WORDS) return null
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
    val words = wordsOf(label)

    return when {
        words.isEmpty() -> false
        "chapter" in words -> true
        words.any { it in NAMED_SECTIONS } -> true
        else -> words.size == 1
    }
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** Deeper than any Markdown heading: the level of an entry opened by a plain marker line. */
private const val PLAIN_MARKER_LEVEL = 7

/**
 * Splits a summary file into its entries, in the order they appear.
 *
 * Text before the first marker line is discarded, which is what absorbs an assistant's "Here are the
 * chapter summaries you asked for." An entry whose body is blank is dropped rather than stored: an
 * empty summary would put a control on a chapter row that opens nothing.
 *
 * A heading deeper than the one that opened an entry belongs to it — `#### Summary` and
 * `#### Analysis` under `### Chapter 1` are the entry's own sub-sections, kept as a bold line. One at
 * the entry's level or above ends it.
 *
 * Each entry also carries the section it sits under (`## Book II`, or a `## Epilogue` with chapters
 * beneath it), which is how a novel that numbers its chapters afresh in every book stays matchable.
 */
fun parseSummaryFile(text: String): List<SummaryEntry> {
    val entries = mutableListOf<SummaryEntry>()
    val body = StringBuilder()
    var label: String? = null
    var labelLevel = PLAIN_MARKER_LEVEL
    var entrySection: String? = null
    var section: String? = null
    var sectionLevel = 0

    fun flush() {
        val current = label ?: return
        val summary = body.toString().trim()
        if (summary.isNotEmpty()) entries += SummaryEntry(current, summary, entrySection)
        body.setLength(0)
    }

    // A heading at or above the section's own level closes it: `## Book III` ends `## Book II`.
    fun leaveSectionsAt(level: Int) {
        if (section != null && level <= sectionLevel) section = null
    }

    // removeSuffix rather than a split on a line-ending regex: a file written on the desktop arrives
    // with CRLF, and a stray carriage return left on the end of a marker line would survive the
    // length test and then fail to normalize.
    for (raw in text.lineSequence()) {
        val line = raw.removeSuffix("\r")
        val heading = headingIn(line)
        when (val kind = classifySummaryLine(line)) {
            is SummaryLine.Marker -> {
                flush()
                heading?.let { leaveSectionsAt(it.level) }
                label = kind.label
                labelLevel = heading?.level ?: PLAIN_MARKER_LEVEL
                entrySection = section
                // "## Epilogue" followed by "### Chapter 1" is a section as much as "## Book II" is.
                val key = normalizeChapterLabel(kind.label)
                if (heading != null && key is ChapterKey.Named) {
                    section = key.name
                    sectionLevel = heading.level
                }
            }
            SummaryLine.StructuralHeading -> {
                val level = heading?.level ?: 0
                if (label != null && heading != null && level > labelLevel) {
                    body.appendLine("**${heading.text}**")
                } else {
                    // Ends whatever entry was open and starts nothing, so a document title or a
                    // "Part One" heading neither joins the summary above it nor becomes an entry.
                    flush()
                    label = null
                    if (heading != null) leaveSectionsAt(level)
                    sectionIn(heading?.text ?: line)?.let {
                        section = it
                        sectionLevel = level
                    }
                }
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
 * Sections narrow that. An entry from `## Book II` / `### Chapter 1` goes to "Book 2 - Chapter 1"
 * before anything else, and a file that restarts its numbering in every book is never matched by
 * chapter number alone against titles that name no book.
 *
 * There is deliberately no fallback to pairing in order. `matchChapters` has one, and it earns its
 * place there: read-along's alternative is the feature not working at all, and `detectOffset` scores
 * candidate alignments against characters per millisecond, a physical quantity that exposes a wrong
 * offset. Nothing here offers an equivalent signal, so a positional guess would give every chapter a
 * confidently wrong summary — a failure indistinguishable, from the outside, from the feature working.
 * Matching nothing and saying so is the better outcome.
 */
fun matchSummaries(entries: List<SummaryEntry>, chapterTitles: Map<Int, String>): SummaryMatch {
    val chapters = withoutConstantSections(
        chapterTitles.entries.sortedBy { it.key }.map { (index, title) -> index to qualifiedKey(title, null) },
    ).filter { it.second.key !is ChapterKey.Unrecognized }.toMutableList()
    val keyed = withoutConstantSections(entries.map { it to qualifiedKey(it.label, it.section) })

    // A file whose books each start again at chapter one. Its entries cannot be matched by number
    // alone against a book whose titles name no book: "Chapter 6" might be book one's or book two's,
    // and guessing is the silent wrong answer the paragraph above refuses.
    val fileRenumbers = keyed.filter { it.second.section != null }
        .groupBy({ it.second.key }, { it.second.section })
        .any { (_, sections) -> sections.distinct().size > 1 }

    fun fallbackAllowed(entry: QualifiedKey, chapter: QualifiedKey) =
        entry.section == null ||
            (chapter.section == null && (entry.key is ChapterKey.Named || !fileRenumbers))

    val matched = linkedMapOf<Int, String>()
    for ((entry, key) in keyed) {
        if (key.key is ChapterKey.Unrecognized) continue
        val pick = chapters.firstOrNull { it.second == key }
            ?: chapters.firstOrNull { it.second.key == key.key && fallbackAllowed(key, it.second) }
            ?: continue
        chapters.remove(pick)
        matched[pick.first] = entry.text
    }

    return SummaryMatch(
        byChapterIndex = matched,
        entryCount = entries.size,
        chapterCount = chapterTitles.size,
    )
}

/** A chapter key and the section it is numbered within, if any: book 2's chapter 1. */
private data class QualifiedKey(val section: String?, val key: ChapterKey)

/**
 * Reads "Book 2 - Chapter 1" as chapter one of section "2", and "Epilogue - Chapter 1" as chapter one
 * of the epilogue. A label naming no section takes [section], the one the file placed it under.
 *
 * Only the words before "chapter" are searched for a section, so a title is never mistaken for one.
 */
private fun qualifiedKey(label: String, section: String?): QualifiedKey {
    val words = wordsOf(label)
    val chapterAt = words.indexOf("chapter")
    if (chapterAt <= 0) return QualifiedKey(section, normalizeChapterLabel(label))

    val prefix = words.take(chapterAt)
    val chapter = normalizeChapterLabel(words.drop(chapterAt).joinToString(" "))
    val named = prefix.firstOrNull { it in NAMED_SECTIONS }
    val numbered = prefix.indices.lastOrNull { prefix[it] in SECTION_WORDS && it + 1 < prefix.size }
        ?.let { sectionNumber(prefix.drop(it + 1)) }
    val own = named ?: numbered
    return when {
        own != null && chapter !is ChapterKey.Unrecognized -> QualifiedKey(own, chapter)
        else -> QualifiedKey(section, normalizeChapterLabel(label))
    }
}

/**
 * Drops sections when fewer than two distinct ones appear. "Mistborn Book 3 - Chapter 12" on every
 * chapter, or a file whose only heading is `## Part One`, says nothing about which chapter is which,
 * and keeping it would only stop those chapters matching a side that never mentions it.
 */
private fun <T> withoutConstantSections(items: List<Pair<T, QualifiedKey>>): List<Pair<T, QualifiedKey>> {
    if (items.mapNotNull { it.second.section }.distinct().size >= 2) return items
    return items.map { (item, key) -> item to key.copy(section = null) }
}
