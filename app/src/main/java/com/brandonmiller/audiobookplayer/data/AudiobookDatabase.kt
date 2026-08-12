package com.brandonmiller.audiobookplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AudiobookEntity::class, ChapterEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AudiobookDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var instance: AudiobookDatabase? = null

        /**
         * Built by hand rather than injected — `config.yaml` rules out a DI framework, and this
         * is the one process-wide singleton the app actually needs.
         *
         * Note there is deliberately no `fallbackToDestructiveMigration`: a future migration
         * failure should be loud, not silently wipe the user's library.
         */
        fun get(context: Context): AudiobookDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudiobookDatabase::class.java,
                    "audiobooks.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
