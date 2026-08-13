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
