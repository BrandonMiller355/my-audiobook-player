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
 * The same treatment [Migration3To4Test] gives the ebook columns, applied to the read-along ones: a
 * real SQLite engine built from the committed version-4 schema export, rather than a hand-written
 * guess at what Room generates.
 *
 * The case that matters is a v4 library with real books in it opening at v5 with every row intact
 * and both new columns null — which reads as "read-along has never been configured", the state the
 * toggle already has to render as "on by default, if supported".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration4To5Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV4Database() {
        val statements = readV4Ddl()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
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
    fun `a v4 book survives with both read-along columns null`() {
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                    lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath,
                                    ebookUri, ebookSpineIndex, ebookCharOffset)
            VALUES (1, 'content://doc/mistborn.m4b', 'M4B', 'The Hero of Ages', 1000, 5000, 0, 45000, 1.25,
                    '/data/covers/1.jpg', 'content://doc/mistborn.epub', 12, 340)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, audiobookId, chapterIndex, title, mediaUri, startPositionMs, endPositionMs)
            VALUES (10, 1, 0, 'Prologue', 'content://doc/mistborn.m4b', 0, 600000)
            """.trimIndent(),
        )

        MIGRATION_4_5.migrate(db)

        val book = db.query(
            """
            SELECT title, lastPositionMs, ebookUri, ebookSpineIndex,
                   readAlongEnabled, readAlongChapterOffset
            FROM audiobooks WHERE id = 1
            """.trimIndent(),
        )
        assertTrue("the existing book survived the migration", book.moveToFirst())
        assertEquals("The Hero of Ages", book.getString(book.getColumnIndexOrThrow("title")))
        assertEquals(45000L, book.getLong(book.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals("content://doc/mistborn.epub", book.getString(book.getColumnIndexOrThrow("ebookUri")))
        listOf("readAlongEnabled", "readAlongChapterOffset").forEach { column ->
            assertTrue(
                "$column is null for a pre-existing book, meaning 'never configured'",
                book.isNull(book.getColumnIndexOrThrow(column)),
            )
        }
        book.close()

        val chapters = db.query("SELECT id FROM chapters WHERE audiobookId = 1")
        assertEquals("the chapter row survived", 1, chapters.count)
        chapters.close()
    }

    @Test
    fun `the migrated database stores a toggle and an offset`() {
        val db = helper.writableDatabase
        MIGRATION_4_5.migrate(db)

        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt,
                                    readAlongEnabled, readAlongChapterOffset)
            VALUES (2, 'content://doc/book.m4b', 'M4B', 'Read Along', 2000, 1, -1)
            """.trimIndent(),
        )

        val cursor = db.query(
            "SELECT readAlongEnabled, readAlongChapterOffset FROM audiobooks WHERE id = 2",
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("readAlongEnabled")))
        assertEquals(-1, cursor.getInt(cursor.getColumnIndexOrThrow("readAlongChapterOffset")))
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on empty tables`() {
        // The common real-world case: the migration runs the first time the app is opened after an
        // upgrade, before any book has necessarily been touched again.
        MIGRATION_4_5.migrate(helper.writableDatabase)
    }

    /** Every `CREATE` statement the committed v4 schema export records, tables before indices. */
    private fun readV4Ddl(): List<String> {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/4.json")
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
