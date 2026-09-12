package com.brandonmiller.audiobookplayer.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The correction queries against a real SQLite engine, for the reason [NoteQueryTest] exists: the
 * claims worth checking here are properties of the schema rather than of Kotlin.
 *
 * The one that matters most is replace-don't-accumulate (`add-readalong-nudge` design D7). Nothing
 * in the reader looks for an existing correction before writing one — it relies entirely on the
 * composite primary key colliding and `OnConflictStrategy.REPLACE` resolving it. If that ever stops
 * being true, the symptom is a chapter quietly carrying two corrections that disagree, and the map
 * interpolating through both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadAlongCorrectionQueryTest {

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
    fun `correcting a chapter twice leaves one correction, the later one`() = runBlocking {
        val book = insertBook("The Hero of Ages")

        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 3, audioMs = 4_472_000, charOffset = 402_118))
        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 3, audioMs = 4_480_000, charOffset = 402_500))

        val stored = dao.readAlongCorrections(book)
        assertEquals("the chapter carries one correction, not two", 1, stored.size)
        assertEquals(4_480_000L, stored.single().audioMs)
        assertEquals(402_500, stored.single().charOffset)
    }

    @Test
    fun `corrections in different chapters coexist`() = runBlocking {
        val book = insertBook("A Book")

        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))
        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 2, audioMs = 1_300_000, charOffset = 120_000))

        assertEquals(2, dao.readAlongCorrections(book).size)
    }

    /** They go into an anchor list that must climb, so the query does the sorting rather than the caller. */
    @Test
    fun `corrections come back in book order`() = runBlocking {
        val book = insertBook("A Book")

        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 3, audioMs = 2_000_000, charOffset = 180_000))
        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))
        dao.upsertReadAlongCorrection(correction(book, chapterIndex = 2, audioMs = 1_300_000, charOffset = 120_000))

        assertEquals(
            listOf(700_000L, 1_300_000L, 2_000_000L),
            dao.readAlongCorrections(book).map { it.audioMs },
        )
    }

    @Test
    fun `one book's corrections are not another's`() = runBlocking {
        val mistborn = insertBook("The Hero of Ages")
        val other = insertBook("Another Book")

        dao.upsertReadAlongCorrection(correction(mistborn, chapterIndex = 3, audioMs = 4_472_000, charOffset = 402_118))

        assertEquals(1, dao.readAlongCorrections(mistborn).size)
        assertEquals("another book is unaffected", 0, dao.readAlongCorrections(other).size)
    }

    /** Design D10: a correction addresses one specific EPUB, so relinking has to discard it. */
    @Test
    fun `clearing a book's corrections leaves every other book's alone`() = runBlocking {
        val relinked = insertBook("Relinked")
        val kept = insertBook("Kept")
        dao.upsertReadAlongCorrection(correction(relinked, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))
        dao.upsertReadAlongCorrection(correction(relinked, chapterIndex = 2, audioMs = 1_300_000, charOffset = 120_000))
        dao.upsertReadAlongCorrection(correction(kept, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))

        dao.clearReadAlongCorrections(relinked)

        assertEquals("the relinked book's corrections went", 0, dao.readAlongCorrections(relinked).size)
        assertEquals("another book's correction is untouched", 1, dao.readAlongCorrections(kept).size)
    }

    @Test
    fun `removing a book takes its corrections`() = runBlocking {
        val kept = insertBook("Kept")
        val removed = insertBook("Removed")
        dao.upsertReadAlongCorrection(correction(removed, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))
        dao.upsertReadAlongCorrection(correction(kept, chapterIndex = 1, audioMs = 700_000, charOffset = 60_000))

        dao.deleteBook(removed)

        assertEquals("the removed book's corrections went with it", 0, dao.readAlongCorrections(removed).size)
        assertEquals("another book's corrections are untouched", 1, dao.readAlongCorrections(kept).size)
    }

    private fun correction(
        audiobookId: Long,
        chapterIndex: Int,
        audioMs: Long,
        charOffset: Int,
    ) = ReadAlongCorrectionEntity(
        audiobookId = audiobookId,
        chapterIndex = chapterIndex,
        audioMs = audioMs,
        charOffset = charOffset,
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
