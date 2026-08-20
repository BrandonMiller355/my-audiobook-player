package com.brandonmiller.audiobookplayer.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
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
import java.io.File

/**
 * [MIGRATION_5_6] drops `readAlongEnabled`, and is the only migration here that is not a plain
 * `ALTER TABLE ADD COLUMN` — SQLite has no `DROP COLUMN` before 3.35 and `minSdk` is 26, so it
 * rebuilds the table instead.
 *
 * **The case worth testing is the chapters.** The rebuild has to `DROP TABLE audiobooks`, and
 * `chapters` holds an `ON DELETE CASCADE` foreign key into it, so with foreign keys enforced that
 * one statement would silently empty the chapter table for every book in the library. It is safe
 * only because Room runs migrations before turning `PRAGMA foreign_keys` on — which is a claim
 * about someone else's code, and therefore exactly the kind of claim to assert rather than believe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration5To6Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV5Database() {
        val statements = readV5Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
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
    fun `a v5 book keeps every other column and loses only the toggle`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)

        MIGRATION_5_6.migrate(db)

        val book = db.query("SELECT * FROM audiobooks WHERE id = 1")
        assertTrue("the existing book survived the rebuild", book.moveToFirst())
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25f, book.getFloat(book.getColumnIndexOrThrow("playbackSpeed")), 0.001f)
        assertEquals("content://doc/mistborn.epub", book.getString(book.getColumnIndexOrThrow("ebookUri")))
        assertEquals(12, book.getInt(book.getColumnIndexOrThrow("ebookSpineIndex")))
        assertEquals("/data/covers/1.jpg", book.getString(book.getColumnIndexOrThrow("artworkPath")))

        // The offset stays; it is the other half of MIGRATION_4_5 and is not what this removes.
        assertEquals(-1, book.getInt(book.getColumnIndexOrThrow("readAlongChapterOffset")))
        assertFalse(
            "readAlongEnabled is gone",
            book.columnNames.contains("readAlongEnabled"),
        )
        book.close()
    }

    /** The cascade this migration's `DROP TABLE` would otherwise trigger. */
    @Test
    fun `dropping the parent table does not cascade the chapters away`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)

        MIGRATION_5_6.migrate(db)

        val chapters = db.query("SELECT id, title FROM chapters WHERE audiobookId = 1 ORDER BY chapterIndex")
        assertEquals("both chapter rows survived", 2, chapters.count)
        chapters.moveToFirst()
        assertEquals("Prologue", chapters.getString(chapters.getColumnIndexOrThrow("title")))
        chapters.close()
    }

    @Test
    fun `the rebuilt table still autoincrements from where it left off`() {
        val db = helper.writableDatabase
        insertBookWithChapters(db)

        MIGRATION_5_6.migrate(db)

        db.execSQL(
            """
            INSERT INTO audiobooks (sourceUri, sourceType, title, addedAt)
            VALUES ('content://doc/second.m4b', 'M4B', 'Second Book', 3000)
            """.trimIndent(),
        )
        val cursor = db.query("SELECT id FROM audiobooks WHERE title = 'Second Book'")
        assertTrue(cursor.moveToFirst())
        assertTrue(
            "a rebuilt table that restarted ids would collide with existing rows",
            cursor.getLong(cursor.getColumnIndexOrThrow("id")) > 1L,
        )
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        MIGRATION_5_6.migrate(helper.writableDatabase)
    }

    /**
     * The rebuilt table is hand-written SQL, so it can disagree with what Room generates from
     * [AudiobookEntity] in a way nothing else here would notice — a wrong affinity or a missing
     * `NOT NULL` still migrates, still stores data, and then fails Room's schema validation the
     * next time the app opens the database. This pins it to the committed v6 export instead.
     */
    @Test
    fun `the rebuilt table matches the schema Room expects at v6`() {
        val db = helper.writableDatabase
        MIGRATION_5_6.migrate(db)

        val actual = mutableMapOf<String, Pair<String, Boolean>>()
        val info = db.query("PRAGMA table_info(audiobooks)")
        while (info.moveToNext()) {
            actual[info.getString(info.getColumnIndexOrThrow("name"))] =
                info.getString(info.getColumnIndexOrThrow("type")) to
                (info.getInt(info.getColumnIndexOrThrow("notnull")) == 1)
        }
        info.close()

        assertEquals("column set differs from Room's v6 entity", expectedV6Columns().keys, actual.keys)
        expectedV6Columns().forEach { (name, expected) ->
            assertEquals("$name has the wrong affinity", expected.first, actual.getValue(name).first)
            assertEquals("$name has the wrong nullability", expected.second, actual.getValue(name).second)
        }
    }

    /** Column name to affinity and not-null, straight from the committed v6 schema export. */
    private fun expectedV6Columns(): Map<String, Pair<String, Boolean>> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/6.json")
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") != "audiobooks") continue
            val fields = entity.getJSONArray("fields")
            return (0 until fields.length()).associate { j ->
                val field = fields.getJSONObject(j)
                // Room omits `notNull` altogether for a nullable column rather than writing false.
                field.getString("columnName") to
                    (field.getString("affinity") to field.optBoolean("notNull", false))
            }
        }
        error("the v6 export has no audiobooks entity")
    }

    private fun insertBookWithChapters(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                    lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath,
                                    ebookUri, ebookSpineIndex, ebookCharOffset,
                                    readAlongEnabled, readAlongChapterOffset)
            VALUES (1, 'content://doc/mistborn.m4b', 'M4B', 'The Hero of Ages', 1000, 5000, 0, 45000, 1.25,
                    '/data/covers/1.jpg', 'content://doc/mistborn.epub', 12, 340, 1, -1)
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

    /** Every `CREATE` statement the committed v5 schema export records, tables before indices. */
    private fun readV5Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/5.json")
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
