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
 * The same treatment [Migration1To2Test] gives `MIGRATION_1_2`, applied to the `.m4b` columns: a
 * real SQLite engine, built from the exact DDL Room recorded in the committed version-2 schema
 * export, rather than a hand-written guess at what Room generates.
 *
 * This one builds both tables, because the migration touches both and because the case that
 * actually matters is a v2 database with a real folder book in it — chapters included — opening at
 * v3 with every row intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration2To3Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV2Database() {
        val statements = readV2Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
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
    fun `a v2 folder book with chapters survives with both new columns null`() {
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                    lastMediaItemIndex, lastPositionMs, playbackSpeed)
            VALUES (1, 'content://tree/primary:Books/Mistborn', 'FOLDER', 'Existing Book', 1000, 5000, 2, 45000, 1.25)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, audiobookId, chapterIndex, title, mediaUri, startPositionMs)
            VALUES (10, 1, 0, 'Chapter 1', 'content://doc/1', 0),
                   (11, 1, 1, 'Chapter 2', 'content://doc/2', 0),
                   (12, 1, 2, 'Chapter 3', 'content://doc/3', 0)
            """.trimIndent(),
        )

        MIGRATION_2_3.migrate(db)

        val book = db.query(
            "SELECT title, lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath FROM audiobooks WHERE id = 1",
        )
        assertTrue("the existing book survived the migration", book.moveToFirst())
        assertEquals("Existing Book", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(2, book.getInt(book.getColumnIndexOrThrow("lastMediaItemIndex")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25, book.getDouble(book.getColumnIndexOrThrow("playbackSpeed")), 0.0001)
        assertTrue(
            "artworkPath is null for a pre-existing book, meaning 'show the placeholder'",
            book.isNull(book.getColumnIndexOrThrow("artworkPath")),
        )
        book.close()

        val chapters = db.query(
            "SELECT chapterIndex, title, mediaUri, startPositionMs, endPositionMs FROM chapters WHERE audiobookId = 1 ORDER BY chapterIndex",
        )
        assertEquals("every chapter row survived", 3, chapters.count)
        var index = 0
        while (chapters.moveToNext()) {
            assertEquals(index, chapters.getInt(chapters.getColumnIndexOrThrow("chapterIndex")))
            assertEquals("Chapter ${index + 1}", chapters.getString(chapters.getColumnIndexOrThrow("title")))
            assertEquals("content://doc/${index + 1}", chapters.getString(chapters.getColumnIndexOrThrow("mediaUri")))
            assertEquals(0L, chapters.getLong(chapters.getColumnIndexOrThrow("startPositionMs")))
            assertTrue(
                "a folder chapter keeps endPositionMs null and resolves its duration at runtime (design D2)",
                chapters.isNull(chapters.getColumnIndexOrThrow("endPositionMs")),
            )
            index++
        }
        chapters.close()
    }

    @Test
    fun `migrated database accepts an m4b book whose chapters carry ends`() {
        val db = helper.writableDatabase

        MIGRATION_2_3.migrate(db)
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, artworkPath)
            VALUES (2, 'content://doc/mistborn.m4b', 'M4B', 'Mistborn', 2000, '/data/covers/2.jpg')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, audiobookId, chapterIndex, title, mediaUri, startPositionMs, endPositionMs)
            VALUES (20, 2, 0, 'Prologue', 'content://doc/mistborn.m4b', 0, 600000),
                   (21, 2, 1, 'Chapter 1', 'content://doc/mistborn.m4b', 600000, 1500000)
            """.trimIndent(),
        )

        val cursor = db.query(
            """
            SELECT a.artworkPath AS artworkPath, c.startPositionMs AS startPositionMs, c.endPositionMs AS endPositionMs
            FROM audiobooks a JOIN chapters c ON c.audiobookId = a.id
            WHERE a.id = 2 AND c.chapterIndex = 1
            """.trimIndent(),
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("/data/covers/2.jpg", cursor.getString(cursor.getColumnIndexOrThrow("artworkPath")))
        assertEquals(600000L, cursor.getLong(cursor.getColumnIndexOrThrow("startPositionMs")))
        assertEquals(1500000L, cursor.getLong(cursor.getColumnIndexOrThrow("endPositionMs")))
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        // The common real-world case: the migration runs the first time the app is opened after an
        // upgrade, before any book has necessarily been touched again.
        MIGRATION_2_3.migrate(helper.writableDatabase)
    }

    /** Every `CREATE` statement the committed v2 schema export records, tables before indices. */
    private fun readV2Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/2.json")
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
