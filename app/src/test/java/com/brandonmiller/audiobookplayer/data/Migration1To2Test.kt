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
 * Runs the real migration against a real SQLite engine, built from the `audiobooks` table's exact
 * DDL as Room recorded it in the committed version-1 schema export — not just a read of the
 * migration's own SQL, and not a hand-written guess at what Room generates.
 *
 * Uses `FrameworkSQLiteOpenHelperFactory` directly rather than `androidx.room:room-testing`'s
 * `MigrationTestHelper`: that library's Room-2.8.x driver-based connection manager and Robolectric
 * disagree on the database's file path (a `SupportSQLiteDriver` "configured to open a database
 * named 'x' but '...full path...' was requested" error), which looks like a genuine version
 * interaction bug rather than anything under this project's control. Opening the database with
 * the same `SupportSQLiteDatabase` type `Migration.migrate()` itself expects sidesteps that
 * entirely, with one fewer dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration1To2Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun openV1Database() {
        val createTableSql = readAudiobooksCreateSql()
        val factory = FrameworkSQLiteOpenHelperFactory()
        helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // in-memory: nothing about this migration is about file I/O
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(createTableSql)
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
    fun `migration adds the new columns and leaves existing rows untouched`() {
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastPlayedAt)
            VALUES (1, 'content://tree/primary:AudiobookTest/Test', 'FOLDER', 'Existing Book', 1000, NULL)
            """.trimIndent(),
        )

        MIGRATION_1_2.migrate(db)

        val cursor = db.query(
            "SELECT title, lastMediaItemIndex, lastPositionMs, playbackSpeed FROM audiobooks WHERE id = 1",
        )
        assertTrue("existing row survived the migration", cursor.moveToFirst())
        assertEquals("Existing Book", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertTrue(
            "new columns are null for a pre-existing row, not defaulted to zero",
            cursor.isNull(cursor.getColumnIndexOrThrow("lastMediaItemIndex")) &&
                cursor.isNull(cursor.getColumnIndexOrThrow("lastPositionMs")) &&
                cursor.isNull(cursor.getColumnIndexOrThrow("playbackSpeed")),
        )
        cursor.close()
    }

    @Test
    fun `migrated database accepts writes to the new columns`() {
        val db = helper.writableDatabase

        MIGRATION_1_2.migrate(db)
        db.execSQL(
            """
            INSERT INTO audiobooks (id, sourceUri, sourceType, title, addedAt, lastMediaItemIndex, lastPositionMs, playbackSpeed)
            VALUES (2, 'content://x', 'FOLDER', 'New Book', 2000, 3, 45000, 1.25)
            """.trimIndent(),
        )

        val cursor = db.query(
            "SELECT lastMediaItemIndex, lastPositionMs, playbackSpeed FROM audiobooks WHERE id = 2",
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("lastMediaItemIndex")))
        assertEquals(45000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastPositionMs")))
        assertEquals(1.25, cursor.getDouble(cursor.getColumnIndexOrThrow("playbackSpeed")), 0.0001)
        cursor.close()
    }

    @Test
    fun `migration runs without an exception on an empty table`() {
        // The common real-world case: the migration runs the first time the app is opened after
        // an upgrade, before any book has necessarily been touched again.
        MIGRATION_1_2.migrate(helper.writableDatabase)
    }

    /** Reads the `audiobooks` table's exact DDL from the committed v1 schema export. */
    private fun readAudiobooksCreateSql(): String {
        val schemaFile = File("schemas/${AudiobookDatabase::class.java.name}/1.json")
        val entities = JSONObject(schemaFile.readText())
            .getJSONObject("database")
            .getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") == "audiobooks") {
                return entity.getString("createSql").replace("\${TABLE_NAME}", "audiobooks")
            }
        }
        error("audiobooks entity not found in the committed v1 schema export")
    }
}
