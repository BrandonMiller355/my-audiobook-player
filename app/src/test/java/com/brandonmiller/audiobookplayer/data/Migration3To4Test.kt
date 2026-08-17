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
 * The same treatment [Migration2To3Test] gives `MIGRATION_2_3`, applied to the ebook columns: a real
 * SQLite engine built from the committed version-3 schema export, rather than a hand-written guess
 * at what Room generates.
 *
 * The case that matters is a v3 library with real books in it opening at v4 with every row intact
 * and all three new columns null — which is exactly "no ebook linked", the state the Player's icon
 * already renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration3To4Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV3Database() {
        val statements = readV3Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
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
    fun `a v3 book survives with all three ebook columns null`() {
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                    lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath)
            VALUES (1, 'content://doc/mistborn.m4b', 'M4B', 'The Hero of Ages', 1000, 5000, 0, 45000, 1.25,
                    '/data/covers/1.jpg')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, audiobookId, chapterIndex, title, mediaUri, startPositionMs, endPositionMs)
            VALUES (10, 1, 0, 'Prologue', 'content://doc/mistborn.m4b', 0, 600000)
            """.trimIndent(),
        )

        MIGRATION_3_4.migrate(db)

        val book = db.query(
            """
            SELECT title, lastPositionMs, playbackSpeed, artworkPath,
                   ebookUri, ebookSpineIndex, ebookCharOffset
            FROM audiobooks WHERE id = 1
            """.trimIndent(),
        )
        assertTrue("the existing book survived the migration", book.moveToFirst())
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25, book.getDouble(book.getColumnIndexOrThrow("playbackSpeed")), 0.0001)
        assertEquals("/data/covers/1.jpg", book.getString(book.getColumnIndexOrThrow("artworkPath")))
        listOf("ebookUri", "ebookSpineIndex", "ebookCharOffset").forEach { column ->
            assertTrue(
                "$column is null for a pre-existing book, meaning 'no ebook linked'",
                book.isNull(book.getColumnIndexOrThrow(column)),
            )
        }
        book.close()

        val chapters = db.query("SELECT id FROM chapters WHERE audiobookId = 1")
        assertEquals("the chapter row survived", 1, chapters.count)
        chapters.close()
    }

    @Test
    fun `the migrated database stores a link and a reading position`() {
        val db = helper.writableDatabase
        MIGRATION_3_4.migrate(db)

        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt,
                                    ebookUri, ebookSpineIndex, ebookCharOffset)
            VALUES (2, 'content://doc/book.m4b', 'M4B', 'Linked', 2000,
                    'content://doc/book.epub', 37, 1204)
            """.trimIndent(),
        )

        val cursor = db.query(
            "SELECT ebookUri, ebookSpineIndex, ebookCharOffset FROM audiobooks WHERE id = 2",
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("content://doc/book.epub", cursor.getString(cursor.getColumnIndexOrThrow("ebookUri")))
        assertEquals(37, cursor.getInt(cursor.getColumnIndexOrThrow("ebookSpineIndex")))
        assertEquals(1204, cursor.getInt(cursor.getColumnIndexOrThrow("ebookCharOffset")))
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        // The common real-world case: the migration runs the first time the app is opened after an
        // upgrade, before any book has necessarily been touched again.
        MIGRATION_3_4.migrate(helper.writableDatabase)
    }

    /** Every `CREATE` statement the committed v3 schema export records, tables before indices. */
    private fun readV3Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/3.json")
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
