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
 * both null, which reads as "never configured" — the toggle then defaults on for a book that
 * supports read-along and the offset defaults to whatever the app detects.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN readAlongEnabled INTEGER")
        db.execSQL("ALTER TABLE audiobooks ADD COLUMN readAlongChapterOffset INTEGER")
    }
}
