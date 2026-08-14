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
