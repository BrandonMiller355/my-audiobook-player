package com.brandonmiller.audiobookplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_TYPE_FOLDER = "FOLDER"

/**
 * A book in the library. [sourceUri] is the SAF tree URI the user picked, held under a
 * persistable read permission — the app never copies the audio itself (PRD §16).
 */
@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val sourceType: String,
    val title: String,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
    /**
     * Saved playback position, stored as raw Media3 player coordinates rather than
     * chapter-domain ones (PRD §19 explicitly allows "simpler equivalent storage"). These
     * round-trip through `controller.seekTo(mediaItemIndex, positionMs)` with no translation for
     * either book shape — for a single-item `.m4b` book, [lastPositionMs] already *is* the
     * absolute book position. Null means no progress has been saved yet.
     */
    val lastMediaItemIndex: Int? = null,
    val lastPositionMs: Long? = null,
    /** This book's own speed. Null means fall back to the last globally used speed (design D5). */
    val playbackSpeed: Float? = null,
)

/**
 * One chapter. Shaped for both book types up front (PRD §19): a folder book gives every chapter
 * its own [mediaUri] with [startPositionMs] zero, while an `.m4b` book will share one URI across
 * chapters distinguished by their start offsets. That way m4b support adds rows, not a migration.
 *
 * Duration is absent on purpose — obtaining it means opening every file, which belongs with the
 * metadata change.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("audiobookId")],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audiobookId: Long,
    val chapterIndex: Int,
    val title: String,
    val mediaUri: String,
    val startPositionMs: Long = 0,
)

/** A library row: the book plus the one derived figure the list shows. */
data class LibraryBook(
    val id: Long,
    val title: String,
    val sourceUri: String,
    val chapterCount: Int,
)
