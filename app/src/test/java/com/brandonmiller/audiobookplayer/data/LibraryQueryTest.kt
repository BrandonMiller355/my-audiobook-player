package com.brandonmiller.audiobookplayer.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `observeLibrary`'s two derived figures, against a real SQLite engine.
 *
 * These are SQL expressions, not Kotlin, so the only way to be sure of them is to run them — in
 * particular the absolute-position subquery, whose whole claim is that one expression covers both
 * book shapes (design D4). A hand-checked reading of the SQL would not have caught, for instance,
 * `SUM` over no rows returning null rather than zero.
 *
 * Robolectric because Room needs a real `Context` and a real SQLite, which is exactly the case
 * `config.yaml` pre-approves it for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryQueryTest {

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
    fun `an m4b book reports its parsed total and its saved position unchanged`() = runBlocking {
        // Every chapter sits at media item 0, so lastPositionMs is already absolute.
        val id = insertBook(
            sourceType = SOURCE_TYPE_M4B,
            lastMediaItemIndex = 0,
            lastPositionMs = 90 * 60_000,
            chapters = listOf(0L to 30 * 60_000L, 30 * 60_000L to 75 * 60_000L, 75 * 60_000L to 120 * 60_000L),
        )

        val book = library().single { it.id == id }
        assertEquals(120 * 60_000L, book.durationMs)
        assertEquals(90 * 60_000L, book.positionMs)
        assertEquals(0.75f, book.progress!!, 0.0001f)
        assertEquals(30 * 60_000L, book.remainingMs)
    }

    @Test
    fun `a folder book part-way through its fourth chapter sums the three before it`() = runBlocking {
        // One media item per chapter, each starting at zero within its own file.
        val id = insertBook(
            sourceType = SOURCE_TYPE_FOLDER,
            lastMediaItemIndex = 3,
            lastPositionMs = 5 * 60_000,
            chapters = List(5) { 0L to 20 * 60_000L },
        )

        val book = library().single { it.id == id }
        assertEquals(100 * 60_000L, book.durationMs)
        assertEquals("three whole chapters plus five minutes into the fourth", 65 * 60_000L, book.positionMs)
        assertEquals(35 * 60_000L, book.remainingMs)
    }

    @Test
    fun `a book that has never been played has no position and no progress`() = runBlocking {
        val id = insertBook(
            sourceType = SOURCE_TYPE_FOLDER,
            lastMediaItemIndex = null,
            lastPositionMs = null,
            chapters = List(3) { 0L to 20 * 60_000L },
        )

        val book = library().single { it.id == id }
        assertEquals("its length is still known", 60 * 60_000L, book.durationMs)
        assertNull(book.positionMs)
        assertNull(book.progress)
    }

    @Test
    fun `a folder book with any chapter unresolved reports no duration at all`() = runBlocking {
        // The case that made this all-or-nothing: a partial sum looks authoritative and is wrong.
        val id = insertBook(
            sourceType = SOURCE_TYPE_FOLDER,
            lastMediaItemIndex = null,
            lastPositionMs = null,
            chapters = listOf(0L to 20 * 60_000L, 0L to null, 0L to 20 * 60_000L),
        )

        val book = library().single { it.id == id }
        assertEquals(3, book.chapterCount)
        assertNull("40 minutes of a three-chapter book is not the book's length", book.durationMs)
        assertNull(book.progress)
        assertNull(book.remainingMs)
    }

    @Test
    fun `a freshly added folder book reports its chapter count alone`() = runBlocking {
        // What every folder book looks like until it has been opened once (design D3).
        val id = insertBook(
            sourceType = SOURCE_TYPE_FOLDER,
            lastMediaItemIndex = null,
            lastPositionMs = null,
            chapters = List(18) { 0L to null },
        )

        val book = library().single { it.id == id }
        assertEquals(18, book.chapterCount)
        assertNull(book.durationMs)
    }

    @Test
    fun `storing a resolved duration makes the total appear`() = runBlocking {
        val id = insertBook(
            sourceType = SOURCE_TYPE_FOLDER,
            lastMediaItemIndex = null,
            lastPositionMs = null,
            chapters = listOf(0L to 20 * 60_000L, 0L to null),
        )
        assertNull(library().single { it.id == id }.durationMs)

        val unresolved = dao.chaptersFor(id).single { it.endPositionMs == null }
        dao.updateChapterEnd(unresolved.id, 25 * 60_000L)

        assertEquals(45 * 60_000L, library().single { it.id == id }.durationMs)
    }

    @Test
    fun `a stored duration is never overwritten by a later pass`() = runBlocking {
        // An .m4b's ends are parsed exactly at add time; a duration read back from the container
        // must not replace one of them.
        val id = insertBook(
            sourceType = SOURCE_TYPE_M4B,
            lastMediaItemIndex = null,
            lastPositionMs = null,
            chapters = listOf(0L to 30 * 60_000L),
        )
        val chapter = dao.chaptersFor(id).single()

        dao.updateChapterEnd(chapter.id, 999L)

        assertEquals(30 * 60_000L, dao.chaptersFor(id).single().endPositionMs)
    }

    private suspend fun library(): List<LibraryBook> = dao.observeLibrary().first()

    /** [chapters] is one `start to end` pair per chapter; a null end means "not yet resolved". */
    private suspend fun insertBook(
        sourceType: String,
        lastMediaItemIndex: Int?,
        lastPositionMs: Long?,
        chapters: List<Pair<Long, Long?>>,
    ): Long = dao.insertBookWithChapters(
        book = AudiobookEntity(
            sourceUri = "content://test/${sourceType.lowercase()}",
            sourceType = sourceType,
            title = "A Book",
            addedAt = 1_000,
            lastPlayedAt = lastPositionMs?.let { 2_000 },
            lastMediaItemIndex = lastMediaItemIndex,
            lastPositionMs = lastPositionMs,
        ),
        chapters = chapters.mapIndexed { index, (start, end) ->
            ChapterEntity(
                audiobookId = 0,
                chapterIndex = index,
                title = "Chapter ${index + 1}",
                mediaUri = "content://test/chapter/$index",
                startPositionMs = start,
                endPositionMs = end,
            )
        },
    )
}
