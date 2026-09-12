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
 * [MIGRATION_7_8] adds the `read_along_corrections` table. Additive, like [MIGRATION_6_7], so the
 * question this pins is the same one: whether the hand-written DDL agrees with what Room generates
 * from [ReadAlongCorrectionEntity]. A disagreement migrates cleanly, stores rows happily, and then
 * fails Room's schema validation the next time the app opens the database
 * (`add-readalong-nudge` design D9).
 *
 * The composite primary key gets its own test rather than being left to the column comparison,
 * because it is load-bearing: design D7's replace-don't-accumulate is enforced by that key
 * colliding, not by anything in the UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration7To8Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV7Database() {
        val statements = readV7Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
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
        insertBookWithChaptersAndNote(db)

        MIGRATION_7_8.migrate(db)

        val book = db.query("SELECT * FROM audiobooks WHERE id = 1")
        assertTrue("the existing book survived", book.moveToFirst())
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals("content://doc/mistborn.epub", book.getString(book.getColumnIndexOrThrow("ebookUri")))
        book.close()

        val chapters = db.query("SELECT title FROM chapters WHERE audiobookId = 1 ORDER BY chapterIndex")
        assertEquals("both chapter rows survived", 2, chapters.count)
        chapters.close()

        val notes = db.query("SELECT text FROM notes WHERE audiobookId = 1")
        assertTrue("the book's note survived", notes.moveToFirst())
        assertEquals("ask about the ending", notes.getString(0))
        notes.close()
    }

    @Test
    fun `a book that predates corrections has none`() {
        val db = helper.writableDatabase
        insertBookWithChaptersAndNote(db)

        MIGRATION_7_8.migrate(db)

        val corrections = db.query("SELECT * FROM read_along_corrections")
        assertEquals(0, corrections.count)
        corrections.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        MIGRATION_7_8.migrate(helper.writableDatabase)
    }

    @Test
    fun `a correction can be written after the migration`() {
        val db = helper.writableDatabase
        insertBookWithChaptersAndNote(db)
        MIGRATION_7_8.migrate(db)

        db.execSQL(
            """
            INSERT INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 3, 4472000, 402118)
            """.trimIndent(),
        )

        val cursor = db.query("SELECT * FROM read_along_corrections WHERE audiobookId = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("chapterIndex")))
        assertEquals(4472000L, cursor.getLong(cursor.getColumnIndexOrThrow("audioMs")))
        assertEquals(402118, cursor.getInt(cursor.getColumnIndexOrThrow("charOffset")))
        cursor.close()
    }

    /**
     * Design D7 in the schema rather than in the UI: a second correction for the same chapter has
     * nowhere to go but on top of the first.
     */
    @Test
    fun `a second correction for the same chapter replaces the first`() {
        val db = helper.writableDatabase
        insertBookWithChaptersAndNote(db)
        MIGRATION_7_8.migrate(db)

        db.execSQL(
            """
            INSERT OR REPLACE INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 3, 4472000, 402118)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 3, 4480000, 402500)
            """.trimIndent(),
        )

        val cursor = db.query("SELECT audioMs, charOffset FROM read_along_corrections WHERE audiobookId = 1")
        assertEquals("the chapter carries one correction, not two", 1, cursor.count)
        assertTrue(cursor.moveToFirst())
        assertEquals("the later correction won", 4480000L, cursor.getLong(0))
        assertEquals(402500, cursor.getInt(1))
        cursor.close()
    }

    /** A different chapter of the same book is a different row, not a collision. */
    @Test
    fun `corrections in different chapters coexist`() {
        val db = helper.writableDatabase
        insertBookWithChaptersAndNote(db)
        MIGRATION_7_8.migrate(db)

        db.execSQL(
            """
            INSERT INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 3, 4472000, 402118), (1, 4, 5600000, 501000)
            """.trimIndent(),
        )

        val cursor = db.query("SELECT chapterIndex FROM read_along_corrections WHERE audiobookId = 1")
        assertEquals(2, cursor.count)
        cursor.close()
    }

    @Test
    fun `the index the query plan depends on is created`() {
        val db = helper.writableDatabase
        MIGRATION_7_8.migrate(db)

        val cursor = db.query("PRAGMA index_list(read_along_corrections)")
        val names = mutableListOf<String>()
        while (cursor.moveToNext()) {
            names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        cursor.close()

        assertTrue(
            "index_read_along_corrections_audiobookId is missing: $names",
            names.contains("index_read_along_corrections_audiobookId"),
        )
    }

    /**
     * The hand-written DDL against the schema Room generated — the mechanical trap design D9 names,
     * and the reason this migration gets a test at all.
     */
    @Test
    fun `the new table matches the schema Room expects at v8`() {
        val db = helper.writableDatabase
        MIGRATION_7_8.migrate(db)

        val actual = mutableMapOf<String, Pair<String, Boolean>>()
        val info = db.query("PRAGMA table_info(read_along_corrections)")
        while (info.moveToNext()) {
            actual[info.getString(info.getColumnIndexOrThrow("name"))] =
                info.getString(info.getColumnIndexOrThrow("type")) to
                    (info.getInt(info.getColumnIndexOrThrow("notnull")) == 1)
        }
        info.close()

        val expected = expectedV8CorrectionColumns()
        assertEquals("column set differs from Room's v8 entity", expected.keys, actual.keys)
        expected.forEach { (name, column) ->
            assertEquals("$name has the wrong affinity", column.first, actual.getValue(name).first)
            assertEquals("$name has the wrong nullability", column.second, actual.getValue(name).second)
        }
    }

    /** The composite key, against the committed export rather than this test's own idea of it. */
    @Test
    fun `the primary key is the book and chapter pair`() {
        val db = helper.writableDatabase
        MIGRATION_7_8.migrate(db)

        val keyColumns = mutableListOf<Pair<Int, String>>()
        val info = db.query("PRAGMA table_info(read_along_corrections)")
        while (info.moveToNext()) {
            val position = info.getInt(info.getColumnIndexOrThrow("pk"))
            if (position > 0) {
                keyColumns += position to info.getString(info.getColumnIndexOrThrow("name"))
            }
        }
        info.close()

        val actual = keyColumns.sortedBy { it.first }.map { it.second }
        assertEquals(expectedV8PrimaryKey(), actual)
    }

    /** The cascade [ReadAlongCorrectionEntity] declares, against the table this migration wrote. */
    @Test
    fun `the foreign key cascades from audiobooks`() {
        val db = helper.writableDatabase
        insertBookWithChaptersAndNote(db)
        MIGRATION_7_8.migrate(db)
        db.execSQL(
            """
            INSERT INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 3, 4472000, 402118)
            """.trimIndent(),
        )
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL("DELETE FROM audiobooks WHERE id = 1")

        val cursor = db.query("SELECT audiobookId FROM read_along_corrections")
        assertEquals("the book's corrections went with it", 0, cursor.count)
        cursor.close()
    }

    /** Column name to affinity and not-null, straight from the committed v8 schema export. */
    private fun expectedV8CorrectionColumns(): Map<String, Pair<String, Boolean>> {
        val fields = correctionEntity().getJSONArray("fields")
        return (0 until fields.length()).associate { i ->
            val field = fields.getJSONObject(i)
            // Room omits `notNull` altogether for a nullable column rather than writing false.
            field.getString("columnName") to
                (field.getString("affinity") to field.optBoolean("notNull", false))
        }
    }

    private fun expectedV8PrimaryKey(): List<String> {
        val columns = correctionEntity().getJSONObject("primaryKey").getJSONArray("columnNames")
        return (0 until columns.length()).map { columns.getString(it) }
    }

    private fun correctionEntity(): JSONObject {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/8.json")
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") == "read_along_corrections") return entity
        }
        error("the v8 export has no read_along_corrections entity")
    }

    private fun insertBookWithChaptersAndNote(db: SupportSQLiteDatabase) {
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
        db.execSQL(
            """
            INSERT INTO notes (audiobookId, mediaItemIndex, positionMs, chapterTitle, text, createdAt)
            VALUES (1, 0, 585000, 'Prologue', 'ask about the ending', 9000)
            """.trimIndent(),
        )
    }

    /** Every `CREATE` statement the committed v7 schema export records, tables before indices. */
    private fun readV7Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/7.json")
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
        // `chapters` and `notes` both have a foreign key into `audiobooks`, so the parent goes first.
        return tables.sortedBy { if (it.contains("`audiobooks`")) 0 else 1 } + indices
    }
}
