package com.brandonmiller.audiobookplayer.playback

import com.brandonmiller.audiobookplayer.ebook.Block
import com.brandonmiller.audiobookplayer.ebook.BlockKind
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.NavEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chapter matching against real measurements from *The Hero of Ages* and its EPUB (design.md
 * "Spike findings", `D:\Claude\Mistborn\BookAndAudiobook\`), rather than synthetic data: the 90
 * audio chapter titles and durations from `ffprobe`, and the 91 table-of-contents labels and their
 * text lengths (the entries between "NAMES AND TERMS" and "EPILOGUE" are dropped — the nav
 * document's play order does not match spine order there (spike finding 6), and reproducing that
 * tangle exactly is not what this test is validating).
 *
 * Task 4.8 and 1.2: ordinal matching should pair all 82 numbered chapters plus the prologue and
 * epilogue, with no offset, and leave the structural and front-matter entries unmatched.
 */
class ChapterMatchingRealBookTest {

    @Test
    fun `ordinal matching pairs every numbered chapter, the prologue, and the epilogue with no offset`() {
        val anchors = matchChapters(audioChapters(), ebook())

        // 82 numbered chapters + Prologue + Epilogue. "Part One"-"Five" and "End Credits" on the
        // audio side, and "ACKNOWLEDGMENTS", "PART ONE"-"FIVE", and "METALS QUICK REFERENCE CHART"
        // on the ebook side, are exactly the leftovers spike finding 1 recorded — none of them here.
        assertEquals(84, anchors.size)

        assertTrue("anchors must be sorted by audio position", anchors.zipWithNext().all { (a, b) -> a.absoluteMs <= b.absoluteMs })
        assertTrue("chars must not run backward", anchors.zipWithNext().all { (a, b) -> a.absoluteChars <= b.absoluteChars })

        // Chapter 1: right after "ACKNOWLEDGMENTS", "PROLOGUE", and the structural "PART ONE".
        assertEquals(ReadAlongAnchor(285_863, 5_235), anchors.first { it.absoluteMs == 285_863L })
        // Chapter 6, the shortest numbered chapter — still pairs correctly at that position.
        assertEquals(ReadAlongAnchor(8_578_698, 117_106), anchors.first { it.absoluteMs == 8_578_698L })
        // The last numbered chapter.
        assertEquals(ReadAlongAnchor(96_892_497, 1_300_154), anchors.first { it.absoluteMs == 96_892_497L })
        // The epilogue.
        assertEquals(ReadAlongAnchor(97_771_797, 1_323_362), anchors.first { it.absoluteMs == 97_771_797L })
    }


    private fun audioChapters(): List<AudioChapter> {
        var cursor = 0L
        return RAW_AUDIO.mapIndexed { index, (title, duration) ->
            AudioChapter(title, ChapterSpan(index, cursor, duration)).also { cursor += duration }
        }
    }

    private fun ebook(): Ebook {
        val blocks = mutableListOf<Block>()
        val contents = mutableListOf<NavEntry>()
        for ((label, chars) in RAW_TOC) {
            contents += NavEntry(label, depth = 0, blockIndex = blocks.size)
            blocks += Block(BlockKind.Paragraph, "x".repeat(chars), spineIndex = 0, charOffset = 0)
        }
        return Ebook(title = "The Hero of Ages", blocks = blocks, contents = contents)
    }

    companion object {
        // title to duration (ms), in playback order — from `ffprobe -show_chapters` on the real file.
        private val RAW_AUDIO = listOf(
            "Prologue" to 278_756L, "Part One" to 7_107L, "Chapter 1" to 1_603_776L, "Chapter 2" to 659_792L,
            "Chapter 3" to 2_023_237L, "Chapter 4" to 1_351_765L, "Chapter 5" to 2_654_265L, "Chapter 6" to 250_894L,
            "Chapter 7" to 658_016L, "Chapter 8" to 1_563_483L, "Chapter 9" to 635_831L, "Chapter 10" to 1_723_007L,
            "Chapter 11" to 1_123_357L, "Chapter 12" to 2_174_423L, "Chapter 13" to 488_579L, "Part Two" to 6_851L,
            "Chapter 14" to 1_504_321L, "Chapter 15" to 936_243L, "Chapter 16" to 2_261_990L, "Chapter 17" to 778_073L,
            "Chapter 18" to 891_363L, "Chapter 19" to 687_731L, "Chapter 20" to 820_135L, "Chapter 21" to 1_678_828L,
            "Chapter 22" to 542_335L, "Chapter 23" to 562_638L, "Chapter 24" to 510_108L, "Chapter 25" to 1_143_863L,
            "Chapter 26" to 1_168_364L, "Chapter 27" to 2_020_643L, "Chapter 28" to 1_031_451L, "Chapter 29" to 1_559_936L,
            "Chapter 30" to 1_933_127L, "Chapter 31" to 1_384_997L, "Chapter 32" to 2_568_732L, "Chapter 33" to 691_413L,
            "Part Three" to 2_997L, "Chapter 34" to 591_550L, "Chapter 35" to 815_623L, "Chapter 36" to 1_471_225L,
            "Chapter 37" to 1_597_683L, "Chapter 38" to 1_123_523L, "Chapter 39" to 520_647L, "Chapter 40" to 1_456_749L,
            "Chapter 41" to 1_363_225L, "Chapter 42" to 798_112L, "Chapter 43" to 1_473_533L, "Chapter 44" to 1_458_616L,
            "Part Four" to 2_997L, "Chapter 45" to 805_054L, "Chapter 46" to 2_006_450L, "Chapter 47" to 1_001_471L,
            "Chapter 48" to 926_245L, "Chapter 49" to 1_851_271L, "Chapter 50" to 787_718L, "Chapter 51" to 980_238L,
            "Chapter 52" to 1_176_507L, "Chapter 53" to 1_444_789L, "Chapter 54" to 956_603L, "Chapter 55" to 1_164_806L,
            "Chapter 56" to 1_196_207L, "Chapter 57" to 913_206L, "Chapter 58" to 2_135_894L, "Part Five" to 2_997L,
            "Chapter 59" to 715_087L, "Chapter 60" to 1_729_240L, "Chapter 61" to 514_712L, "Chapter 62" to 1_307_976L,
            "Chapter 63" to 1_774_520L, "Chapter 64" to 424_246L, "Chapter 65" to 1_549_880L, "Chapter 66" to 742_628L,
            "Chapter 67" to 624_898L, "Chapter 68" to 990_620L, "Chapter 69" to 678_721L, "Chapter 70" to 939_194L,
            "Chapter 71" to 1_246_810L, "Chapter 72" to 1_461_277L, "Chapter 73" to 1_318_096L, "Chapter 74" to 471_429L,
            "Chapter 75" to 1_538_000L, "Chapter 76" to 975_859L, "Chapter 77" to 469_649L, "Chapter 78" to 1_071_943L,
            "Chapter 79" to 640_824L, "Chapter 80" to 1_164_525L, "Chapter 81" to 2_636_997L, "Chapter 82" to 879_300L,
            "Epilogue" to 132_048L, "End Credits" to 751_043L,
        )

        // label to text length (chars), in table-of-contents order — from `EpubParser` on the real file.
        private val RAW_TOC = listOf(
            "ACKNOWLEDGMENTS" to 1_771, "PROLOGUE" to 3_395, "PART ONE" to 69, "1" to 21_807, "2" to 8_220,
            "3" to 29_098, "4" to 17_328, "5" to 35_418, "6" to 3_009, "7" to 8_525, "8" to 20_609, "9" to 8_201,
            "10" to 22_515, "11" to 12_904, "12" to 27_872, "13" to 5_551, "PART TWO" to 542, "14" to 20_624,
            "15" to 12_705, "16" to 30_481, "17" to 9_750, "18" to 11_756, "19" to 8_570, "20" to 10_311,
            "21" to 22_624, "22" to 7_020, "23" to 6_919, "24" to 6_629, "25" to 14_880, "26" to 15_344,
            "27" to 27_547, "28" to 13_752, "29" to 21_437, "30" to 26_298, "31" to 18_258, "32" to 34_393,
            "33" to 8_862, "PART THREE" to 326, "34" to 8_119, "35" to 11_528, "36" to 19_846, "37" to 23_888,
            "38" to 14_326, "39" to 7_113, "40" to 20_443, "41" to 19_530, "42" to 10_828, "43" to 21_044,
            "44" to 19_684, "PART FOUR" to 403, "45" to 10_676, "46" to 27_991, "47" to 13_218, "48" to 13_897,
            "49" to 25_488, "50" to 11_868, "51" to 15_687, "52" to 15_870, "53" to 19_823, "54" to 12_271,
            "55" to 15_834, "56" to 15_006, "57" to 11_777, "58" to 29_548, "PART FIVE" to 492, "59" to 10_676,
            "60" to 22_812, "61" to 7_010, "62" to 17_109, "63" to 22_564, "64" to 5_800, "65" to 19_660,
            "66" to 9_739, "67" to 8_767, "68" to 11_825, "69" to 9_233, "70" to 12_501, "71" to 15_315,
            "72" to 19_173, "73" to 18_221, "74" to 5_918, "75" to 19_811, "76" to 11_836, "77" to 7_679,
            "78" to 14_748, "79" to 8_050, "80" to 16_769, "81" to 33_420, "82" to 22_117,
            "METALS QUICK REFERENCE CHART" to 1_091, "EPILOGUE" to 8_765,
        )
    }
}
