package com.brandonmiller.audiobookplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_TYPE_FOLDER = "FOLDER"
const val SOURCE_TYPE_M4B = "M4B"

/**
 * A book in the library. [sourceUri] is what the user picked, held under a persistable read
 * permission — a SAF tree URI for a folder book, a single document URI for an `.m4b` — and the
 * app never copies the audio itself (PRD §16).
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
    /**
     * Path to the downsampled cover cached in app-private storage, or null for "show the
     * placeholder" (`add-m4b-books` design D7). The bytes are never stored here: a blob column
     * would be read by the library query on every emission.
     */
    val artworkPath: String? = null,
)

/**
 * One chapter. Shaped for both book types up front (PRD §19): a folder book gives every chapter
 * its own [mediaUri] with [startPositionMs] zero, while an `.m4b` book shares one URI across
 * chapters distinguished by their start offsets. That way m4b support added rows, not a migration.
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
    /**
     * Where this chapter ends within [mediaUri], or null when that is not known.
     *
     * An `.m4b`'s ends come free with a parse that is already happening, and storing them makes
     * the scrubber's total exact the moment the book opens. A folder chapter keeps this null and
     * resolves its duration at runtime instead: obtaining it means opening every one of up to 128
     * files, which is why `add-folder-audiobooks` design D5 chose not to persist it
     * (`add-m4b-books` design D2).
     */
    val endPositionMs: Long? = null,
)

/** A library row: the book plus the derived figures the list and the resume card show. */
data class LibraryBook(
    val id: Long,
    val title: String,
    val sourceUri: String,
    val chapterCount: Int,
    val artworkPath: String? = null,
    /**
     * Total length, or null when it is not yet known — which is every folder book that has not
     * been opened since it was added, because a folder book's chapter durations are read on its
     * first open rather than at add time (`redesign-player-and-library` design D3).
     *
     * Null rather than a partial sum on purpose. A total assembled from the chapters that happen
     * to have resolved looks authoritative and is wrong; absent is a figure the UI knows not to
     * draw.
     */
    val durationMs: Long? = null,
    /**
     * Saved position across the whole book, or null when the book has never been played. Derived
     * from the stored player coordinates rather than stored separately (design D4).
     */
    val positionMs: Long? = null,
    /** When this book was last played, or null if it never has been — the resume card's tiebreak. */
    val lastPlayedAt: Long? = null,
) {
    /**
     * How far through the book the saved position is, or null when either figure is missing. Both
     * are needed: a position without a total says nothing about progress.
     */
    val progress: Float?
        get() {
            val position = positionMs ?: return null
            val duration = durationMs?.takeIf { it > 0 } ?: return null
            return (position.toFloat() / duration).coerceIn(0f, 1f)
        }

    /** What is left to listen to, or null when the total is not known. */
    val remainingMs: Long?
        get() {
            val duration = durationMs ?: return null
            return (duration - (positionMs ?: 0)).coerceAtLeast(0)
        }
}
