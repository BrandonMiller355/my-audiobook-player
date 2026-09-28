package com.brandonmiller.audiobookplayer.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A book that is an ebook alone, and the one way it gains audio (`add-standalone-ebooks`), against a
 * real SQLite engine — the library query's derived figures have to come out as "no audio" rather
 * than as a zero-length book, and the attach has to be all-or-nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandaloneEbookQueryTest {

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
    fun `an ebook alone is listed with no audio and no figures`() = runBlocking {
        val id = insertEbookOnly()

        val book = library().single { it.id == id }
        assertFalse(book.hasAudio)
        assertTrue(book.hasEbook)
        assertNull(book.sourceUri)
        assertEquals(EBOOK_URI, book.ebookUri)
        assertEquals(0, book.chapterCount)
        assertNull("no audio is not zero-length audio", book.durationMs)
        assertNull(book.positionMs)
        assertNull(book.progress)
    }

    @Test
    fun `attaching audio fills in the same row and keeps the reading position`() = runBlocking {
        val id = insertEbookOnly()
        dao.updateReadingPosition(id, spineIndex = 7, charOffset = 1_234)

        val attached = dao.attachAudio(id, AUDIO_URI, SOURCE_TYPE_M4B, chapters(3))

        assertTrue(attached)
        val entity = dao.findBook(id)!!
        assertEquals(AUDIO_URI, entity.sourceUri)
        assertEquals(SOURCE_TYPE_M4B, entity.sourceType)
        assertEquals("the title the owner has been seeing stays", "The Well of Ascension", entity.title)
        assertEquals(EBOOK_URI, entity.ebookUri)
        assertEquals(7, entity.ebookSpineIndex)
        assertEquals(1_234, entity.ebookCharOffset)
        assertEquals(listOf(0, 1, 2), dao.chaptersFor(id).map { it.chapterIndex })

        val row = library().single { it.id == id }
        assertTrue(row.hasAudio)
        assertEquals(3, row.chapterCount)
        assertEquals(3 * 60_000L, row.durationMs)
        assertEquals("still one book, not two", 1, library().size)
    }

    @Test
    fun `audio is never attached to a book that already has it`() = runBlocking {
        val id = insertEbookOnly()
        dao.attachAudio(id, AUDIO_URI, SOURCE_TYPE_M4B, chapters(3))

        val second = dao.attachAudio(id, "content://test/other", SOURCE_TYPE_FOLDER, chapters(5))

        assertFalse(second)
        assertEquals(AUDIO_URI, dao.findBook(id)!!.sourceUri)
        assertEquals("the first scan's chapters, uninterleaved", 3, dao.chaptersFor(id).size)
    }

    @Test
    fun `an ebook alone cannot be unlinked into an empty row`() = runBlocking {
        val id = insertEbookOnly()

        dao.unlinkEbook(id)

        assertEquals(EBOOK_URI, dao.findBook(id)!!.ebookUri)
    }

    @Test
    fun `a book with audio can still be unlinked`() = runBlocking {
        val id = insertEbookOnly()
        dao.attachAudio(id, AUDIO_URI, SOURCE_TYPE_M4B, chapters(1))

        dao.unlinkEbook(id)

        assertNull(dao.findBook(id)!!.ebookUri)
    }

    private suspend fun library(): List<LibraryBook> = dao.observeLibrary().first()

    private suspend fun insertEbookOnly(): Long = dao.insertBook(
        AudiobookEntity(
            sourceUri = null,
            sourceType = null,
            title = "The Well of Ascension",
            addedAt = 1_000,
            ebookUri = EBOOK_URI,
        ),
    )

    private fun chapters(count: Int) = List(count) { index ->
        ChapterEntity(
            audiobookId = 0,
            chapterIndex = index,
            title = "Chapter ${index + 1}",
            mediaUri = AUDIO_URI,
            startPositionMs = index * 60_000L,
            endPositionMs = (index + 1) * 60_000L,
        )
    }

    private companion object {
        const val EBOOK_URI = "content://test/well.epub"
        const val AUDIO_URI = "content://test/well.m4b"
    }
}
