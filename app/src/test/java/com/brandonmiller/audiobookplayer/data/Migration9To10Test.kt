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
 * [MIGRATION_9_10] rebuilds `audiobooks` so a book can be an ebook alone, with no audio source
 * (`add-standalone-ebooks` design D1).
 *
 * **The case worth testing is every child table.** The rebuild drops `audiobooks`, and four tables
 * cascade from it — one more than when [MIGRATION_5_6] did the same thing. With foreign keys
 * enforced that one statement would empty chapters, notes, corrections, and summaries for every book
 * in the library. It is safe only because Room runs migrations before turning `PRAGMA foreign_keys`
 * on, which is a claim about someone else's code and so asserted here rather than believed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration9To10Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV9Database() {
        val statements = readDdl(9)
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
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
    fun `an existing book keeps every column`() {
        val db = helper.writableDatabase
        insertBookWithEverythingV9Holds(db)

        MIGRATION_9_10.migrate(db)

        val book = db.query("SELECT * FROM audiobooks WHERE id = 1")
        assertTrue("the existing book survived the rebuild", book.moveToFirst())
        assertEquals("content://doc/mistborn.m4b", book.getString(book.getColumnIndexOrThrow("sourceUri")))
        assertEquals("M4B", book.getString(book.getColumnIndexOrThrow("sourceType")))
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(1000L, book.getLong(book.getColumnIndexOrThrow("addedAt")))
        assertEquals(5000L, book.getLong(book.getColumnIndexOrThrow("lastPlayedAt")))
        assertEquals(0, book.getInt(book.getColumnIndexOrThrow("lastMediaItemIndex")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25f, book.getFloat(book.getColumnIndexOrThrow("playbackSpeed")), 0.001f)
        assertEquals("/data/covers/1.jpg", book.getString(book.getColumnIndexOrThrow("artworkPath")))
        assertEquals("content://doc/mistborn.epub", book.getString(book.getColumnIndexOrThrow("ebookUri")))
        assertEquals(12, book.getInt(book.getColumnIndexOrThrow("ebookSpineIndex")))
        assertEquals(340, book.getInt(book.getColumnIndexOrThrow("ebookCharOffset")))
        assertEquals(-1, book.getInt(book.getColumnIndexOrThrow("readAlongChapterOffset")))
        book.close()
    }

    @Test
    fun `every child row survives the drop`() {
        val db = helper.writableDatabase
        insertBookWithEverythingV9Holds(db)

        MIGRATION_9_10.migrate(db)

        listOf("chapters" to 2, "notes" to 1, "read_along_corrections" to 1, "chapter_summaries" to 1)
            .forEach { (table, expected) ->
                val cursor = db.query("SELECT * FROM $table WHERE audiobookId = 1")
                assertEquals("$table lost rows in the rebuild", expected, cursor.count)
                cursor.close()
            }
    }

    @Test
    fun `a book with no audio source can be written after the migration`() {
        val db = helper.writableDatabase
        MIGRATION_9_10.migrate(db)

        db.execSQL(
            """
            INSERT INTO audiobooks (sourceUri, sourceType, title, addedAt, ebookUri)
            VALUES (NULL, NULL, 'The Well of Ascension', 2000, 'content://doc/well.epub')
            """.trimIndent(),
        )

        val cursor = db.query("SELECT sourceUri, sourceType FROM audiobooks")
        assertTrue(cursor.moveToFirst())
        assertTrue("sourceUri stays null", cursor.isNull(0))
        assertTrue("sourceType stays null", cursor.isNull(1))
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        MIGRATION_9_10.migrate(helper.writableDatabase)
    }

    /**
     * The rebuilt table against the schema Room generated from [AudiobookEntity] — a mismatch
     * migrates cleanly and then fails validation the next time the app opens the database.
     */
    @Test
    fun `the rebuilt table matches the schema Room expects at v10`() {
        val db = helper.writableDatabase
        MIGRATION_9_10.migrate(db)

        val actual = mutableMapOf<String, Pair<String, Boolean>>()
        val info = db.query("PRAGMA table_info(audiobooks)")
        while (info.moveToNext()) {
            actual[info.getString(info.getColumnIndexOrThrow("name"))] =
                info.getString(info.getColumnIndexOrThrow("type")) to
                    (info.getInt(info.getColumnIndexOrThrow("notnull")) == 1)
        }
        info.close()

        val expected = expectedV10AudiobookColumns()
        assertEquals("column set differs from Room's v10 entity", expected.keys, actual.keys)
        expected.forEach { (name, column) ->
            assertEquals("$name has the wrong affinity", column.first, actual.getValue(name).first)
            assertEquals("$name has the wrong nullability", column.second, actual.getValue(name).second)
        }
    }

    /** Column name to affinity and not-null, straight from the committed v10 schema export. */
    private fun expectedV10AudiobookColumns(): Map<String, Pair<String, Boolean>> {
        val fields = entity(10, "audiobooks").getJSONArray("fields")
        return (0 until fields.length()).associate { i ->
            val field = fields.getJSONObject(i)
            // Room omits `notNull` altogether for a nullable column rather than writing false.
            field.getString("columnName") to
                (field.getString("affinity") to field.optBoolean("notNull", false))
        }
    }

    private fun insertBookWithEverythingV9Holds(db: SupportSQLiteDatabase) {
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
        db.execSQL(
            """
            INSERT INTO read_along_corrections (audiobookId, chapterIndex, audioMs, charOffset)
            VALUES (1, 1, 4472000, 402118)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapter_summaries (audiobookId, chapterIndex, `text`, prompted)
            VALUES (1, 1, 'Vin considers the Deepness.', 0)
            """.trimIndent(),
        )
    }

    private fun entities(version: Int) =
        JSONObject(File("schemas/${AudiobookDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
            .getJSONArray("entities")

    private fun entity(version: Int, tableName: String): JSONObject {
        val entities = entities(version)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") == tableName) return entity
        }
        error("the v$version export has no $tableName entity")
    }

    /** Every `CREATE` statement a committed schema export records, tables before indices. */
    private fun readDdl(version: Int): List<String> {
        val entities = entities(version)
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
        // Every other table has a foreign key into `audiobooks`, so the parent goes first.
        return tables.sortedBy { if (it.contains("`audiobooks`")) 0 else 1 } + indices
    }
}
