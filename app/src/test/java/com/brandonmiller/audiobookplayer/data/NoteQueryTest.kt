package com.brandonmiller.audiobookplayer.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The note queries against a real SQLite engine, for the same reason [LibraryQueryTest] exists: they
 * are SQL, and the two claims worth checking — that the cascade is really declared, and that the
 * note count did not quietly break the library query it was added to — are both invisible in Kotlin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteQueryTest {

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
    fun `removing a book takes its notes and leaves every other book's alone`() = runBlocking {
        val kept = insertBook("Kept")
        val removed = insertBook("Removed")
        dao.insertNote(note(removed, mediaItemIndex = 0, positionMs = 1_000))
        dao.insertNote(note(removed, mediaItemIndex = 1, positionMs = 2_000))
        dao.insertNote(note(kept, mediaItemIndex = 0, positionMs = 3_000))

        dao.deleteBook(removed)

        assertEquals("the removed book's notes went with it", 0, notesFor(removed).size)
        assertEquals("another book's notes are untouched", 1, notesFor(kept).size)
    }

    @Test
    fun `notes are listed in book order rather than the order they were taken`() = runBlocking {
        val book = insertBook("A Book")
        // Inserted deliberately out of order, and with two in the same media item.
        dao.insertNote(note(book, mediaItemIndex = 3, positionMs = 500, text = "fourth"))
        dao.insertNote(note(book, mediaItemIndex = 1, positionMs = 9_000, text = "third"))
        dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 8_000, text = "second"))
        dao.insertNote(note(book, mediaItemIndex = 1, positionMs = 100, text = "second-and-a-half"))
        dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 200, text = "first"))

        val ordered = notesFor(book).map { it.text }

        assertEquals(
            listOf("first", "second", "second-and-a-half", "third", "fourth"),
            ordered,
        )
    }

    @Test
    fun `an m4b book's notes sort by position alone`() = runBlocking {
        // Every chapter shares media item 0, so the tiebreak column is doing all the work.
        val book = insertBook("The Hero of Ages")
        dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 4 * 3_600_000, text = "late"))
        dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 600_000, text = "early"))

        assertEquals(listOf("early", "late"), notesFor(book).map { it.text })
    }

    @Test
    fun `a mark becomes a note without moving or changing its anchor`() = runBlocking {
        val book = insertBook("A Book")
        val id = dao.insertNote(note(book, mediaItemIndex = 2, positionMs = 7_000, text = null))
        dao.insertNote(note(book, mediaItemIndex = 5, positionMs = 0, text = "later in the book"))

        dao.updateNoteText(id, "written up afterwards")

        val notes = notesFor(book)
        assertEquals("still first in book order", id, notes.first().id)
        assertEquals("written up afterwards", notes.first().text)
        assertEquals("the anchor is untouched", 2, notes.first().mediaItemIndex)
        assertEquals(7_000L, notes.first().positionMs)
        assertEquals("Chapter 3", notes.first().chapterTitle)
    }

    @Test
    fun `clearing the text leaves a mark rather than deleting the record`() = runBlocking {
        val book = insertBook("A Book")
        val id = dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 100, text = "a thought"))

        dao.updateNoteText(id, null)

        val remaining = notesFor(book).single()
        assertEquals(id, remaining.id)
        assertNull("it is a bare mark again", remaining.text)
    }

    @Test
    fun `deleting one note leaves the rest`() = runBlocking {
        val book = insertBook("A Book")
        val first = dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 100, text = "one"))
        dao.insertNote(note(book, mediaItemIndex = 1, positionMs = 100, text = "two"))

        dao.deleteNote(first)

        assertEquals(listOf("two"), notesFor(book).map { it.text })
    }

    @Test
    fun `the library row carries the note count`() = runBlocking {
        val book = insertBook("A Book")
        assertEquals(0, libraryRow(book).noteCount)

        dao.insertNote(note(book, mediaItemIndex = 0, positionMs = 100))
        dao.insertNote(note(book, mediaItemIndex = 1, positionMs = 100))

        assertEquals(2, libraryRow(book).noteCount)
    }

    /**
     * The regression the DAO comment warns about. `observeLibrary` aggregates over a join to
     * `chapters`, so if the note count were ever rewritten as a second `LEFT JOIN` the rows would
     * multiply and both of these figures would silently inflate — three notes would turn a
     * two-chapter book into a six-chapter one three times its real length.
     */
    @Test
    fun `notes do not inflate the chapter count or the duration`() = runBlocking {
        val book = insertBook("A Book", chapters = 2)
        val before = libraryRow(book)

        repeat(3) { dao.insertNote(note(book, mediaItemIndex = 0, positionMs = it * 1_000L)) }

        val after = libraryRow(book)
        assertEquals("chapter count is unchanged by notes", before.chapterCount, after.chapterCount)
        assertEquals("duration is unchanged by notes", before.durationMs, after.durationMs)
        assertEquals(2, after.chapterCount)
        assertTrue("the notes were really written", after.noteCount == 3)
    }

    private suspend fun notesFor(bookId: Long): List<NoteEntity> = dao.observeNotes(bookId).first()

    private suspend fun libraryRow(bookId: Long): LibraryBook =
        dao.observeLibrary().first().single { it.id == bookId }

    private fun note(
        audiobookId: Long,
        mediaItemIndex: Int,
        positionMs: Long,
        text: String? = null,
    ) = NoteEntity(
        audiobookId = audiobookId,
        mediaItemIndex = mediaItemIndex,
        positionMs = positionMs,
        chapterTitle = "Chapter ${mediaItemIndex + 1}",
        text = text,
        createdAt = 1_000,
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
