package com.brandonmiller.audiobookplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases are taken from the shapes that actually exist in the owner's library. */
class NaturalOrderTest {

    private fun sorted(vararg names: String) = names.toList().sortedWith(NaturalOrder)

    @Test
    fun `unpadded chapter numbers sort numerically, not lexicographically`() {
        assertEquals(
            listOf("Chapter 1.mp3", "Chapter 2.mp3", "Chapter 3.mp3", "Chapter 10.mp3"),
            sorted("Chapter 10.mp3", "Chapter 2.mp3", "Chapter 1.mp3", "Chapter 3.mp3"),
        )
    }

    @Test
    fun `disc and track names order by disc then track`() {
        // Born To Run: 128 files across nine discs.
        assertEquals(
            listOf("1-01 Intro.mp3", "1-09 Ch2e.mp3", "1-10 Ch3a.mp3", "2-01 Ch7a.mp3", "9-15 The End.mp3"),
            sorted("2-01 Ch7a.mp3", "9-15 The End.mp3", "1-10 Ch3a.mp3", "1-01 Intro.mp3", "1-09 Ch2e.mp3"),
        )
    }

    @Test
    fun `numbers surrounded by words order correctly`() {
        // I Am Legend: "01 of 09" .. "09 of 09".
        assertEquals(
            listOf("I Am Legend - 01 of 09.mp3", "I Am Legend - 02 of 09.mp3", "I Am Legend - 09 of 09.mp3"),
            sorted("I Am Legend - 09 of 09.mp3", "I Am Legend - 02 of 09.mp3", "I Am Legend - 01 of 09.mp3"),
        )
    }

    @Test
    fun `multi-part numbering within a book orders correctly`() {
        assertEquals(
            listOf(
                "Andy Weir - Project Hail Mary - 01.mp3",
                "Andy Weir - Project Hail Mary - 09.mp3",
                "Andy Weir - Project Hail Mary - 10.mp3",
                "Andy Weir - Project Hail Mary - 30.mp3",
            ),
            sorted(
                "Andy Weir - Project Hail Mary - 30.mp3",
                "Andy Weir - Project Hail Mary - 10.mp3",
                "Andy Weir - Project Hail Mary - 01.mp3",
                "Andy Weir - Project Hail Mary - 09.mp3",
            ),
        )
    }

    @Test
    fun `names without digits sort case-insensitively`() {
        assertEquals(
            listOf("alpha.mp3", "Beta.mp3", "gamma.mp3"),
            sorted("gamma.mp3", "alpha.mp3", "Beta.mp3"),
        )
    }

    @Test
    fun `leading zeros do not change numeric value`() {
        assertEquals(0, NaturalOrder.compare("track 007.mp3", "track 7.mp3"))
        assertEquals(
            listOf("track 007.mp3", "track 8.mp3"),
            sorted("track 8.mp3", "track 007.mp3"),
        )
    }

    @Test
    fun `digit runs longer than a Long do not overflow or mis-sort`() {
        val big = "file " + "9".repeat(30) + ".mp3"
        val bigger = "file " + "9".repeat(31) + ".mp3"
        assertEquals(listOf(big, bigger), sorted(bigger, big))
    }

    @Test
    fun `a name that is a prefix of another sorts first`() {
        assertEquals(listOf("Chapter", "Chapter 1"), sorted("Chapter 1", "Chapter"))
        assertEquals(listOf("Intro", "Intro Part 2"), sorted("Intro Part 2", "Intro"))
    }

    @Test
    fun `punctuation compares by character, so a space sorts before a dot`() {
        // Not a prefix relationship: once extensions are included this compares ' ' against '.'.
        // Either order is defensible; this pins the one the comparator actually produces.
        assertEquals(
            listOf("Chapter 1.mp3", "Chapter.mp3"),
            sorted("Chapter.mp3", "Chapter 1.mp3"),
        )
    }

    @Test
    fun `equal names compare equal so sorting stays stable`() {
        assertEquals(0, NaturalOrder.compare("same 1.mp3", "same 1.mp3"))
    }
}
