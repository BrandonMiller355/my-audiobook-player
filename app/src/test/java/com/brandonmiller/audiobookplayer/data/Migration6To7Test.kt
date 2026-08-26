package com.brandonmiller.audiobookplayer.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * [MIGRATION_6_7] adds the `notes` table. It is additive, so the interesting question is not whether
 * the new table appears — it is whether the hand-written DDL agrees with what Room generates from
 * [NoteEntity], because a disagreement migrates cleanly, stores rows happily, and then fails Room's
 * schema validation the next time the app opens the database (design D10). That is what
 * `the new table matches the schema Room expects at v7` pins, against the committed export rather
 * than against a second copy of the same assumption.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV6Database() {
        val statements = readV6Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            statements.forEach(db::execSQL)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
    }

    @After
    fun closeDatabase() {
        helper.close()
    }

    @Test
    fun `an existing library is untouched by the migration`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)

        MIGRATION_6_7.migrate(db)

        val book = db.query("SELECT * FROM audiobooks WHERE id = 1")
        assertTrue("the existing book survived", book.moveToFirst())
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25f, book.getFloat(book.getColumnIndexOrThrow("playbackSpeed")), 0.001f)
        assertEquals("content://doc/mistborn.epub", book.getString(book.getColumnIndexOrThrow("ebookUri")))
        book.close()

        val chapters = db.query("SELECT title FROM chapters WHERE audiobookId = 1 ORDER BY chapterIndex")
        assertEquals("both chapter rows survived", 2, chapters.count)
        chapters.close()
    }

    @Test
    fun `the notes table exists and starts empty`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)

        MIGRATION_6_7.migrate(db)

        val notes = db.query("SELECT * FROM notes")
        assertEquals("a book that existed before notes did has none", 0, notes.count)
        notes.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        MIGRATION_6_7.migrate(helper.writableDatabase)
    }

    @Test
    fun `a note can be written after the migration`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)
        MIGRATION_6_7.migrate(db)

        db.execSQL(
            """
            INSERT INTO notes (audiobookId, mediaItemIndex, positionMs, chapterTitle, text, createdAt)
            VALUES (1, 0, 585000, 'Prologue', 'ask about the ending', 9000)
            """.trimIndent(),
        )

        val cursor = db.query("SELECT * FROM notes WHERE audiobookId = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(585000L, cursor.getLong(cursor.getColumnIndexOrThrow("positionMs")))
        assertEquals("Prologue", cursor.getString(cursor.getColumnIndexOrThrow("chapterTitle")))
        assertEquals("ask about the ending", cursor.getString(cursor.getColumnIndexOrThrow("text")))
        cursor.close()
    }

    /** A bare mark — the null [NoteEntity.text] that distinguishes a bookmark from a note (D1). */
    @Test
    fun `a mark with no text is a valid row`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)
        MIGRATION_6_7.migrate(db)

        db.execSQL(
            """
            INSERT INTO notes (audiobookId, mediaItemIndex, positionMs, chapterTitle, createdAt)
            VALUES (1, 1, 12000, 'Chapter 1', 9000)
            """.trimIndent(),
        )

        val cursor = db.query("SELECT text FROM notes WHERE audiobookId = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue("a bare mark stores a null text", cursor.isNull(0))
        cursor.close()
    }

    @Test
    fun `the index the query plan depends on is created`() {
        val db = helper.writableDatabase
        MIGRATION_6_7.migrate(db)

        val cursor = db.query("PRAGMA index_list(notes)")
        val names = mutableListOf<String>()
        while (cursor.moveToNext()) {
            names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        cursor.close()

        assertTrue("index_notes_audiobookId is missing: $names", names.contains("index_notes_audiobookId"))
    }

    /**
     * The hand-written DDL against the schema Room generated — the failure mode design D10 calls the
     * mechanical trap, and the reason this migration gets a test at all.
     */
    @Test
    fun `the new table matches the schema Room expects at v7`() {
        val db = helper.writableDatabase
        MIGRATION_6_7.migrate(db)

        val actual = mutableMapOf<String, Pair<String, Boolean>>()
        val info = db.query("PRAGMA table_info(notes)")
        while (info.moveToNext()) {
            actual[info.getString(info.getColumnIndexOrThrow("name"))] =
                info.getString(info.getColumnIndexOrThrow("type")) to
                (info.getInt(info.getColumnIndexOrThrow("notnull")) == 1)
        }
        info.close()

        val expected = expectedV7NoteColumns()
        assertEquals("column set differs from Room's v7 entity", expected.keys, actual.keys)
        expected.forEach { (name, column) ->
            assertEquals("$name has the wrong affinity", column.first, actual.getValue(name).first)
            assertEquals("$name has the wrong nullability", column.second, actual.getValue(name).second)
        }
    }

    /** The cascade [NoteEntity] declares, asserted against the table this migration actually wrote. */
    @Test
    fun `the foreign key cascades from audiobooks`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)
        MIGRATION_6_7.migrate(db)
        db.execSQL(
            """
            INSERT INTO notes (audiobookId, mediaItemIndex, positionMs, chapterTitle, createdAt)
            VALUES (1, 0, 585000, 'Prologue', 9000)
            """.trimIndent(),
        )
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL("DELETE FROM audiobooks WHERE id = 1")

        val cursor = db.query("SELECT id FROM notes")
        assertEquals("the book's notes went with it", 0, cursor.count)
        cursor.close()
    }

    /** Column name to affinity and not-null, straight from the committed v7 schema export. */
    private fun expectedV7NoteColumns(): Map<String, Pair<String, Boolean>> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/7.json")
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") != "notes") continue
            val fields = entity.getJSONArray("fields")
            return (0 until fields.length()).associate { j ->
                val field = fields.getJSONObject(j)
                // Room omits `notNull` altogether for a nullable column rather than writing false.
                field.getString("columnName") to
                    (field.getString("affinity") to field.optBoolean("notNull", false))
            }
        }
        error("the v7 export has no notes entity")
    }

    private fun insertBookWithChapters(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                    lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath,
                                    ebookUri, ebookSpineIndex, ebookCharOffset, readAlongChapterOffset)
            VALUES (1, 'content://doc/mistborn.m4b', 'M4B', 'The Hero of Ages', 1000, 5000, 0, 45000, 1.25,
                    '/data/covers/1.jpg', 'content://doc/mistborn.epub', 12, 340, -1)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, audiobookId, chapterIndex, title, mediaUri, startPositionMs, endPositionMs)
            VALUES (10, 1, 0, 'Prologue', 'content://doc/mistborn.m4b', 0, 600000),
                   (11, 1, 1, 'Chapter 1', 'content://doc/mistborn.m4b', 600000, 1200000)
            """.trimIndent(),
        )
    }

    /** Every `CREATE` statement the committed v6 schema export records, tables before indices. */
    private fun readV6Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/6.json")
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        val tables = mutableListOf<String>()
        val indices = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            tables += entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            val declared = entity.optJSONArray("indices") ?: continue
            for (j in 0 until declared.length()) {
                indices += declared.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", tableName)
            }
        }
        // `chapters` has a foreign key into `audiobooks`, so the parent has to exist first.
        return tables.sortedBy { if (it.contains("`audiobooks`")) 0 else 1 } + indices
    }
}
