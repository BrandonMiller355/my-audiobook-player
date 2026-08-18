package com.brandonmiller.audiobookplayer.playback

import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.NavEntry
import kotlin.math.sqrt

/** One audio chapter: its title, for label matching, and its extent, for the audio side of an anchor. */
data class AudioChapter(val title: String, val span: ChapterSpan)

/**
 * What a normalized chapter label reduces to (design D3). Two labels denote the same chapter when
 * they normalize to equal keys, regardless of notation.
 */
sealed interface ChapterKey {
    data class Numbered(val ordinal: Int) : ChapterKey
    data class Named(val name: String) : ChapterKey
    data object Unrecognized : ChapterKey
}

/**
 * Pairs audio chapters to table-of-contents entries and converts the pairing into the anchor list
 * [ReadAlongMap] interpolates over (design D3, D1). Both a chapter mark and a table-of-contents entry
 * can be structural rather than a chapter — a "Part One" heading is real on both sides — so each is
 * filtered before matching rather than after.
 *
 * [manualOffset] is the owner's correction, applied on top of whatever offset the fallback path
 * detects (D3, task 4.6). It does nothing when label matching succeeds, since there is then no
 * offset to correct.
 */
fun matchChapters(audioChapters: List<AudioChapter>, ebook: Ebook, manualOffset: Int = 0): List<ReadAlongAnchor> {
    val audio = audioChapterCandidates(audioChapters)
    val toc = tocChapterCandidates(ebook)
    if (audio.isEmpty() || toc.isEmpty()) return emptyList()

    val pairs = matchByLabel(audio, toc).ifEmpty { matchByOrder(audio, toc, manualOffset) }
    return toAnchors(pairs)
}

// ------------------------------------------------------------------ structural filtering

/** Below this, an audio chapter mark is structural — spike finding 2's seven-second "Part One". */
private const val MIN_CHAPTER_DURATION_MS = 60_000L

/** Below this, a table-of-contents entry is a heading with no chapter of its own beneath it. */
private const val MIN_CHAPTER_CHARS = 1_000

private data class AudioCandidate(val title: String, val span: ChapterSpan)

private data class TocCandidate(val entry: NavEntry, val startChars: Int, val endChars: Int)

private fun audioChapterCandidates(chapters: List<AudioChapter>): List<AudioCandidate> =
    chapters.mapNotNull { chapter ->
        val duration = chapter.span.durationMs ?: return@mapNotNull null
        if (duration < MIN_CHAPTER_DURATION_MS) null else AudioCandidate(chapter.title, chapter.span)
    }

/**
 * A range per entry from consecutive [NavEntry.blockIndex] values, in table-of-contents order — not
 * resorted into block order. A nav document whose play order does not match spine order (spike
 * finding 6) then yields one entry a too-large range and its neighbor a too-small or negative one,
 * and the too-small side falls under [MIN_CHAPTER_CHARS] and is filtered rather than mismatched.
 */
private fun tocChapterCandidates(ebook: Ebook): List<TocCandidate> {
    val contents = ebook.contents
    return contents.indices.mapNotNull { i ->
        val entry = contents[i]
        val startChars = ebook.absoluteCharsAt(entry.blockIndex)
        val endBlock = contents.getOrNull(i + 1)?.blockIndex
        val endChars = if (endBlock == null) ebook.totalCharacters else ebook.absoluteCharsAt(endBlock)
        if (endChars - startChars < MIN_CHAPTER_CHARS) null else TocCandidate(entry, startChars, endChars)
    }
}

// ------------------------------------------------------------------ label normalization

private val NAMED_SECTIONS = setOf("prologue", "epilogue", "prelude", "interlude", "appendix")

private val SPELLED_OUT_NUMBERS = mapOf(
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
    "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
    "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
    "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
)

private val ROMAN_NUMERAL = Regex("^m{0,4}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$")
private val ROMAN_VALUES = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)

private val FILE_EXTENSION = Regex("\\.[a-z0-9]{2,4}$")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/**
 * Reduces a chapter title or table-of-contents label to what it denotes: a named section, a chapter
 * number (however written), or nothing recognizable (design D3).
 *
 * The digit, roman-numeral, and spelled-out-number searches only look at the word following
 * "chapter", or at the whole label when it is three words or fewer. A longer, unprefixed title is
 * where a false hit lives — an English word that happens to be valid Roman numerals ("MIX"), or a
 * number word buried in a sentence — so those are left `Unrecognized` rather than guessed at.
 */
internal fun normalizeChapterLabel(label: String): ChapterKey {
    val cleaned = label.lowercase().replace(FILE_EXTENSION, "").replace(NON_ALPHANUMERIC, " ").trim()
    if (cleaned.isEmpty()) return ChapterKey.Unrecognized
    val words = cleaned.split(' ').filter { it.isNotEmpty() }

    NAMED_SECTIONS.firstOrNull { it in words }?.let { return ChapterKey.Named(it) }

    val chapterAt = words.indexOf("chapter")
    val window = when {
        chapterAt >= 0 -> words.drop(chapterAt + 1)
        words.size <= 3 -> words
        else -> emptyList()
    }

    window.firstOrNull { it.all(Char::isDigit) }
        ?.let { return ChapterKey.Numbered(it.trimStart('0').ifEmpty { "0" }.toInt()) }
    window.firstOrNull { ROMAN_NUMERAL.matches(it) }
        ?.let { return ChapterKey.Numbered(romanToInt(it)) }
    spelledOutNumber(window)
        ?.let { return ChapterKey.Numbered(it) }

    return ChapterKey.Unrecognized
}

private fun romanToInt(numeral: String): Int {
    var total = 0
    for (i in numeral.indices) {
        val value = ROMAN_VALUES.getValue(numeral[i])
        val next = numeral.getOrNull(i + 1)?.let { ROMAN_VALUES[it] } ?: 0
        total += if (value < next) -value else value
    }
    return total
}

/** "twenty one" (tens + ones) or a lone number word. */
private fun spelledOutNumber(words: List<String>): Int? {
    val first = words.firstOrNull()?.let { SPELLED_OUT_NUMBERS[it] } ?: return null
    if (first >= 20 && first % 10 == 0) {
        val second = words.getOrNull(1)?.let { SPELLED_OUT_NUMBERS[it] }
        if (second != null && second in 1..9) return first + second
    }
    return first
}

// ------------------------------------------------------------------ matching

private fun matchByLabel(audio: List<AudioCandidate>, toc: List<TocCandidate>): List<Pair<AudioCandidate, TocCandidate>> {
    val tocByKey = toc.groupBy { normalizeChapterLabel(it.entry.label) }.toMutableMap()
    val pairs = mutableListOf<Pair<AudioCandidate, TocCandidate>>()

    for (chapter in audio) {
        val key = normalizeChapterLabel(chapter.title)
        if (key is ChapterKey.Unrecognized) continue
        val candidates = tocByKey[key] ?: continue
        val match = candidates.firstOrNull() ?: continue
        pairs += chapter to match
        tocByKey[key] = candidates - match
    }
    return pairs
}

/**
 * Pairs chapters in order, from the alignment [detectOffset] finds most consistent, then applies
 * [manualOffset] as a further correction on top of it (task 4.6).
 */
private fun matchByOrder(
    audio: List<AudioCandidate>,
    toc: List<TocCandidate>,
    manualOffset: Int,
): List<Pair<AudioCandidate, TocCandidate>> {
    val offset = detectOffset(audio, toc) + manualOffset
    return audio.indices.mapNotNull { i -> toc.getOrNull(i + offset)?.let { audio[i] to it } }
}

/**
 * Scores every offset for which at least [MIN_PAIRS_FOR_SCORING] pairs exist by the coefficient of
 * variation of the implied characters-per-millisecond across those pairs, and returns the lowest —
 * the alignment under which reading rate is most consistent (design D3, spike finding 7).
 */
private fun detectOffset(audio: List<AudioCandidate>, toc: List<TocCandidate>): Int {
    if (audio.isEmpty() || toc.isEmpty()) return 0

    var bestOffset = 0
    var bestScore = Double.POSITIVE_INFINITY
    for (offset in -(toc.size - 1)..(audio.size - 1)) {
        val rates = audio.indices.mapNotNull { i ->
            val entry = toc.getOrNull(i + offset) ?: return@mapNotNull null
            val duration = audio[i].span.durationMs?.takeIf { it > 0 } ?: return@mapNotNull null
            (entry.endChars - entry.startChars).toDouble() / duration
        }
        if (rates.size < MIN_PAIRS_FOR_SCORING) continue

        val score = coefficientOfVariation(rates)
        if (score < bestScore) {
            bestScore = score
            bestOffset = offset
        }
    }
    return bestOffset
}

private const val MIN_PAIRS_FOR_SCORING = 5

private fun coefficientOfVariation(rates: List<Double>): Double {
    val mean = rates.average()
    if (mean <= 0.0) return Double.POSITIVE_INFINITY
    val variance = rates.sumOf { (it - mean) * (it - mean) } / rates.size
    return sqrt(variance) / mean
}

/**
 * Converts matched pairs into the sorted anchor list [ReadAlongMap] expects, dropping any pair that
 * would make the character side run backward — defensive against a stray mismatch rather than
 * something the real data has been seen to need.
 */
private fun toAnchors(pairs: List<Pair<AudioCandidate, TocCandidate>>): List<ReadAlongAnchor> {
    val anchors = mutableListOf<ReadAlongAnchor>()
    for ((audio, toc) in pairs.sortedBy { it.first.span.absoluteStartMs }) {
        if (anchors.isNotEmpty() && toc.startChars < anchors.last().absoluteChars) continue
        anchors += ReadAlongAnchor(audio.span.absoluteStartMs, toc.startChars)
    }
    return anchors
}
