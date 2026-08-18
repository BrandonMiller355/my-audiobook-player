package com.brandonmiller.audiobookplayer.playback

import com.brandonmiller.audiobookplayer.ebook.Block
import com.brandonmiller.audiobookplayer.ebook.BlockKind
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.NavEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterMatchingTest {

    // ------------------------------------------------------------------ label normalization

    @Test
    fun `differing notation for the same chapter normalizes to the same key`() {
        val expected = ChapterKey.Numbered(3)

        assertEquals(expected, normalizeChapterLabel("03 - Chapter 3"))
        assertEquals(expected, normalizeChapterLabel("3"))
        assertEquals(expected, normalizeChapterLabel("Chapter III"))
        assertEquals(expected, normalizeChapterLabel("Chapter Three"))
    }

    @Test
    fun `named sections normalize regardless of case or punctuation`() {
        assertEquals(ChapterKey.Named("prologue"), normalizeChapterLabel("PROLOGUE"))
        assertEquals(ChapterKey.Named("epilogue"), normalizeChapterLabel("Epilogue."))
        assertEquals(ChapterKey.Named("appendix"), normalizeChapterLabel("Appendix"))
    }

    @Test
    fun `a title with nothing to match on is unrecognized`() {
        assertEquals(ChapterKey.Unrecognized, normalizeChapterLabel("Acknowledgments"))
        assertEquals(ChapterKey.Unrecognized, normalizeChapterLabel("End Credits"))
    }

    @Test
    fun `an english word that is also valid roman numerals is not mistaken for one`() {
        // "MIX" parses as a roman numeral (1009) under a naive scan; it is not preceded by "chapter"
        // and is not a bare 1-3 word label on its own, so it stays unrecognized.
        assertEquals(ChapterKey.Unrecognized, normalizeChapterLabel("The Mix of Metals and Fear"))
    }

    // ------------------------------------------------------------------ matching by label

    @Test
    fun `chapters written in different notation are matched to each other`() {
        val audio = listOf(chapterAudio("03 - Chapter 3", 3))
        val ebook = ebookOf(listOf(entry("Chapter III", chars = 5_000)))

        val anchors = matchChapters(audio, ebook)

        assertEquals(1, anchors.size)
        assertEquals(audio.single().span.absoluteStartMs, anchors.single().absoluteMs)
    }

    @Test
    fun `named sections on both sides are matched to each other rather than by position`() {
        val audio = listOf(chapterAudio("Prologue", 0), chapterAudio("Chapter 1", 1), chapterAudio("Epilogue", 2))
        val ebook = ebookOf(
            listOf(
                entry("PROLOGUE", chars = 4_000),
                entry("1", chars = 20_000),
                entry("EPILOGUE", chars = 6_000),
            ),
        )

        val anchors = matchChapters(audio, ebook)

        assertEquals(3, anchors.size)
        // In audio-start order, so in the same order the sections themselves appear.
        assertEquals(listOf(0L, chapterMs(1), chapterMs(2)), anchors.map { it.absoluteMs })
    }

    @Test
    fun `front matter the audio does not narrate is not matched, and later chapters still are`() {
        val audio = listOf(chapterAudio("Chapter 1", 0))
        val ebook = ebookOf(
            listOf(
                entry("Copyright", chars = 1_500), // present, but no audio chapter matches it
                entry("1", chars = 20_000),
            ),
        )

        val anchors = matchChapters(audio, ebook)

        assertEquals(1, anchors.size)
        // Anchored to where "1"'s text starts — right after the unmatched copyright page's 1,500
        // characters — not shifted to the wrong place by it being there.
        assertEquals(1_500, anchors.single().absoluteChars)
    }

    @Test
    fun `a part heading immediately before its first chapter is not treated as a chapter`() {
        val audio = listOf(chapterAudio("Chapter 1", 0))
        val ebook = ebookOf(
            listOf(
                entry("PART ONE", chars = 60), // no text of its own — structural
                entry("1", chars = 20_000),
            ),
        )

        val anchors = matchChapters(audio, ebook)

        assertEquals(1, anchors.size)
        // "1" still starts after PART ONE's own (short) text, even though PART ONE itself was
        // filtered out as a chapter candidate.
        assertEquals(60, anchors.single().absoluteChars)
    }

    // ------------------------------------------------------------------ fallback to order

    @Test
    fun `chapters with nothing in common on their labels are matched in order`() {
        val audio = listOf(chapterAudio("Track 1", 0), chapterAudio("Track 2", 1), chapterAudio("Track 3", 2))
        val ebook = ebookOf(
            listOf(
                entry("Section A", chars = 12_000),
                entry("Section B", chars = 14_000),
                entry("Section C", chars = 13_000),
            ),
        )

        val anchors = matchChapters(audio, ebook)

        assertEquals(3, anchors.size)
        assertEquals(listOf(0, 12_000, 12_000 + 14_000), anchors.map { it.absoluteChars })
    }

    @Test
    fun `an offset is detected rather than asked for when order matching starts at different places`() {
        // The ebook's table of contents carries one extra front-matter entry the audio has nothing
        // for, so every chapter after it is one position ahead. Each audio chapter's duration is
        // built from its matching text at a fixed narration rate, so the *correct* alignment (+1,
        // skipping "Copyright") is the one where the implied rate comes out constant — any other
        // alignment scrambles chars against the wrong durations and its rate swings wildly.
        val charsPerMs = 0.1
        val chapterChars = listOf(8_000, 30_000, 12_000, 40_000, 9_000, 35_000)
        val durations = chapterChars.map { (it / charsPerMs).toLong() }
        var cursor = 0L
        val audio = chapterChars.indices.map { i ->
            AudioChapter("Track ${i + 1}", ChapterSpan(i, cursor, durations[i])).also { cursor += durations[i] }
        }
        val entries = listOf(entry("Copyright", chars = 1_100)) +
            chapterChars.mapIndexed { i, chars -> entry("Untitled ${i + 1}", chars) }
        val ebook = ebookOf(entries)

        val anchors = matchChapters(audio, ebook)

        assertEquals(chapterChars.size, anchors.size)
        // Chapter 1's audio anchors to the *second* toc entry's text (index 1), skipping "Copyright".
        assertEquals(startOfUntitled(chapterChars, index = 0), anchors[0].absoluteChars)
        assertEquals(startOfUntitled(chapterChars, index = 3), anchors[3].absoluteChars)
    }

    @Test
    fun `a manual offset corrects a matching that is off by a constant`() {
        val audio = listOf(chapterAudio("Track 1", 0), chapterAudio("Track 2", 1))
        val ebook = ebookOf(
            listOf(
                entry("Untitled A", chars = 10_000),
                entry("Untitled B", chars = 20_000),
                entry("Untitled C", chars = 15_000),
            ),
        )

        // With no manual correction and too few pairs to auto-detect an offset, order-fallback
        // pairs from the very first entry — wrong, off by one.
        val uncorrected = matchChapters(audio, ebook)
        assertEquals(0, uncorrected[0].absoluteChars)

        // Shifting by +1 corrects the whole book with one number.
        val corrected = matchChapters(audio, ebook, manualOffset = 1)
        assertEquals(10_000, corrected[0].absoluteChars)
        assertEquals(30_000, corrected[1].absoluteChars)
    }

    // ------------------------------------------------------------------ unavailable cases

    @Test
    fun `no audio chapters yields no anchors`() {
        val ebook = ebookOf(listOf(entry("1", chars = 10_000)))

        assertTrue(matchChapters(emptyList(), ebook).isEmpty())
    }

    @Test
    fun `no table of contents yields no anchors`() {
        val audio = listOf(chapterAudio("Chapter 1", 0))
        val ebook = ebookOf(emptyList())

        assertTrue(matchChapters(audio, ebook).isEmpty())
    }

    // ------------------------------------------------------------------ fixtures

    private fun chapterMs(index: Int) = index * 300_000L

    private fun chapterAudio(title: String, index: Int, durationMs: Long = 300_000): AudioChapter =
        AudioChapter(title, ChapterSpan(index, chapterMs(index), durationMs))

    private fun entry(label: String, chars: Int) = label to chars

    /** Builds an [Ebook] whose blocks reproduce the requested char count per table-of-contents entry. */
    private fun ebookOf(entries: List<Pair<String, Int>>): Ebook {
        val blocks = mutableListOf<Block>()
        val contents = mutableListOf<NavEntry>()
        for ((label, chars) in entries) {
            contents += NavEntry(label, depth = 0, blockIndex = blocks.size)
            blocks += Block(BlockKind.Paragraph, "x".repeat(chars), spineIndex = 0, charOffset = 0)
        }
        return Ebook(title = "Book", blocks = blocks, contents = contents)
    }

    /** The start, in absolute characters, of "Untitled N" when "Copyright" (1,100 chars) precedes it. */
    private fun startOfUntitled(chapterChars: List<Int>, index: Int): Int = chapterChars.take(index).sum() + 1_100
}
