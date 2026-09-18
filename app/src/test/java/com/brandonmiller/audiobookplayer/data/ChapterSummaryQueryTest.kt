package com.brandonmiller.audiobookplayer.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The summary queries against a real SQLite engine, for the reason [ReadAlongCorrectionQueryTest]
 * exists: the claims worth checking here are properties of the schema rather than of Kotlin.
 *
 * The one that matters most is replace-don't-merge (`add-chapter-summaries` design D5). Correcting a
 * summary means editing the file and importing it again, so an entry the owner *deleted* from the
 * file has to disappear from the book. If the replace ever degrades into an upsert, the symptom is a
 * chapter still showing text from an import the owner thought they had thrown away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterSummaryQueryTest {

    private lateinit var database: AudiobookDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AudiobookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.libraryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an import replaces what the book carried rather than merging with it`() = runBlocking {
        val book = insertBook("The Hero of Ages")
        dao.replaceChapterSummaries(
            book,
            listOf(summary(book, 0, "The first import, chapter one."), summary(book, 1, "The first import, chapter two.")),
        )

        // The corrected file has an entry for chapter one only -- chapter two's was deleted from it.
        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "The corrected import.")))

        val stored = dao.chapterSummaries(book)
        assertEquals(1, stored.size)
        assertEquals(0, stored.single().chapterIndex)
        assertEquals("The corrected import.", stored.single().text)
    }

    @Test
    fun `summaries come back in chapter order`() = runBlocking {
        val book = insertBook("A Book")

        dao.replaceChapterSummaries(
            book,
            listOf(summary(book, 3, "Fourth."), summary(book, 0, "First."), summary(book, 2, "Third.")),
        )

        assertEquals(listOf(0, 2, 3), dao.chapterSummaries(book).map { it.chapterIndex })
    }

    @Test
    fun `one book's summaries are not another's`() = runBlocking {
        val mistborn = insertBook("The Hero of Ages")
        val other = insertBook("Another Book")

        dao.replaceChapterSummaries(mistborn, listOf(summary(mistborn, 0, "Vin.")))

        assertEquals(1, dao.chapterSummaries(mistborn).size)
        assertEquals("another book is unaffected", 0, dao.chapterSummaries(other).size)
    }

    @Test
    fun `importing for one book leaves every other book's summaries alone`() = runBlocking {
        val reimported = insertBook("Reimported")
        val kept = insertBook("Kept")
        dao.replaceChapterSummaries(reimported, listOf(summary(reimported, 0, "Old.")))
        dao.replaceChapterSummaries(kept, listOf(summary(kept, 0, "Kept.")))

        dao.replaceChapterSummaries(reimported, listOf(summary(reimported, 1, "New.")))

        assertEquals(listOf(1), dao.chapterSummaries(reimported).map { it.chapterIndex })
        assertEquals("another book's summary is untouched", "Kept.", dao.chapterSummaries(kept).single().text)
    }

    @Test
    fun `removing a book takes its summaries`() = runBlocking {
        val kept = insertBook("Kept")
        val removed = insertBook("Removed")
        dao.replaceChapterSummaries(removed, listOf(summary(removed, 0, "Going.")))
        dao.replaceChapterSummaries(kept, listOf(summary(kept, 0, "Staying.")))

        dao.deleteBook(removed)

        assertEquals("the removed book's summaries went with it", 0, dao.chapterSummaries(removed).size)
        assertEquals("another book's summaries are untouched", 1, dao.chapterSummaries(kept).size)
    }

    /**
     * Design D6's at-most-once, and the reason the flag is set by its own statement rather than by
     * writing the row back: the Player holds a copy of the summary when it makes the offer, and a
     * re-import may have replaced the text in between.
     */
    @Test
    fun `marking a chapter prompted changes nothing but the flag`() = runBlocking {
        val book = insertBook("A Book")
        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "Vin."), summary(book, 1, "Kelsier.")))

        dao.markChapterSummaryPrompted(book, chapterIndex = 0)

        val stored = dao.chapterSummaries(book)
        assertTrue("the crossed chapter is flagged", stored.single { it.chapterIndex == 0 }.prompted)
        assertEquals("its text is untouched", "Vin.", stored.single { it.chapterIndex == 0 }.text)
        assertFalse("the next chapter is not flagged", stored.single { it.chapterIndex == 1 }.prompted)
    }

    @Test
    fun `marking a chapter that has no summary does nothing`() = runBlocking {
        val book = insertBook("A Book")
        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "Vin.")))

        dao.markChapterSummaryPrompted(book, chapterIndex = 2)

        assertEquals(1, dao.chapterSummaries(book).size)
    }

    /** Design D5 clears the flags along with everything else, which is what lets a re-import offer again. */
    @Test
    fun `re-importing clears a prompted flag`() = runBlocking {
        val book = insertBook("A Book")
        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "Vin.")))
        dao.markChapterSummaryPrompted(book, chapterIndex = 0)

        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "Vin, corrected.")))

        assertFalse(dao.chapterSummaries(book).single().prompted)
    }

    /**
     * Observed rather than read once: the import happens with the chapter sheet open, and its rows
     * have to gain their controls without the sheet being dismissed and reopened.
     */
    @Test
    fun `the observed summaries reflect an import`() = runBlocking {
        val book = insertBook("A Book")
        assertEquals(0, dao.observeChapterSummaries(book).first().size)

        dao.replaceChapterSummaries(book, listOf(summary(book, 0, "Vin.")))

        assertEquals(1, dao.observeChapterSummaries(book).first().size)
    }

    private fun summary(audiobookId: Long, chapterIndex: Int, text: String) = ChapterSummaryEntity(
        audiobookId = audiobookId,
        chapterIndex = chapterIndex,
        text = text,
    )

    private suspend fun insertBook(title: String, chapters: Int = 4): Long =
        dao.insertBookWithChapters(
            book = AudiobookEntity(
                sourceUri = "content://test/${title.lowercase().replace(' ', '-')}",
                sourceType = SOURCE_TYPE_M4B,
                title = title,
                addedAt = 1_000,
            ),
            chapters = List(chapters) { index ->
                ChapterEntity(
                    audiobookId = 0,
                    chapterIndex = index,
                    title = "Chapter ${index + 1}",
                    mediaUri = "content://test/audio",
                    startPositionMs = index * 600_000L,
                    endPositionMs = (index + 1) * 600_000L,
                )
            },
        )
}
