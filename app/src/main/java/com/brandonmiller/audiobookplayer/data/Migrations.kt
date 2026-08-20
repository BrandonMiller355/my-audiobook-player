package com.brandonmiller.audiobookplayer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds saved-progress and per-book-speed columns (`add-transport-controls`). Additive and
 * non-destructive: every existing row keeps working with these simply absent until the book is
 * next played. `fallbackToDestructiveMigration` is deliberately never used
 * (`add-folder-audiobooks` design D7) — a schema change must be a real migration, not a silent wipe.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN lastMediaItemIndex INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN lastPositionMs INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN playbackSpeed REAL")
    }
}

/**
 * Adds the two columns `.m4b` books need (`add-m4b-books` design D2 and D7). Additive and
 * non-destructive in the same shape as [MIGRATION_1_2], with no backfill: an existing folder book
 * keeps both null and behaves exactly as before — a null `endPositionMs` is the "duration not yet
 * known" state `BookTimeline` already handles, and a null `artworkPath` shows the placeholder.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN endPositionMs INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN artworkPath TEXT")
    }
}

/**
 * Adds the linked ebook and the place the user stopped reading in it (`add-ebook-companion`
 * design D4). Additive and non-destructive in the same shape as the two above, with no backfill:
 * an existing book has no ebook, and all three null is exactly the state the Player's icon already
 * has to render as "link one".
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN ebookUri TEXT")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN ebookSpineIndex INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN ebookCharOffset INTEGER")
    }
}

/**
 * Adds the read-along toggle and chapter offset (`add-readalong-scroll` design D9). Additive and
 * non-destructive in the same shape as the three above, with no backfill: an existing book keeps
 * both null, which reads as "never configured".
 *
 * `readAlongEnabled` is dropped again by [MIGRATION_5_6]. It stays here because this migration
 * already ran on a real device: what a migration did is history, and rewriting it would leave that
 * database at version 5 with a column this no longer claims to add.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN readAlongEnabled INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN readAlongChapterOffset INTEGER")
    }
}

/**
 * Drops `readAlongEnabled`. The toggle it backed is gone — pausing the audio already stops the text
 * following it, so the control earned nothing for the column, the three gating branches, and the
 * chrome slot it cost.
 *
 * The only migration here that is not a plain `ALTER TABLE ADD COLUMN`, because SQLite has no
 * `DROP COLUMN` before 3.35 (Android 14) and `minSdk` is 26. So it is the table rebuild SQLite's
 * own "Making Other Kinds Of Table Schema Changes" prescribes: build the replacement, copy every
 * row across by name, drop the original, rename.
 *
 * **The dangerous part is the drop**, not the rebuild: `chapters` holds a `ON DELETE CASCADE`
 * foreign key into `audiobooks`, so dropping the parent with foreign keys enforced would take every
 * chapter in the library with it. It is safe only because Room runs migrations before enabling
 * `PRAGMA foreign_keys`, and safe is not a thing to assert from a doc comment — `Migration5To6Test`
 * asserts the chapters are still there afterwards.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE audiobooks_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceUri TEXT NOT NULL,
                sourceType TEXT NOT NULL,
                title TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                lastPlayedAt INTEGER,
                lastMediaItemIndex INTEGER,
                lastPositionMs INTEGER,
                playbackSpeed REAL,
                artworkPath TEXT,
                ebookUri TEXT,
                ebookSpineIndex INTEGER,
                ebookCharOffset INTEGER,
                readAlongChapterOffset INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO audiobooks_new (id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                                        lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath,
                                        ebookUri, ebookSpineIndex, ebookCharOffset, readAlongChapterOffset)
            SELECT id, sourceUri, sourceType, title, addedAt, lastPlayedAt,
                   lastMediaItemIndex, lastPositionMs, playbackSpeed, artworkPath,
                   ebookUri, ebookSpineIndex, ebookCharOffset, readAlongChapterOffset
            FROM audiobooks
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE audiobooks")
        db.execSQL("ALTER TABLE audiobooks_new RENAME TO audiobooks")
    }
}
