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
    /**
     * The linked EPUB's document URI, held under a persistable read grant, or null for "no ebook"
     * (`add-ebook-companion` design D4). As with the audio, the file itself is never copied.
     *
     * Columns rather than a table because a book has at most one ebook: a join table would be
     * ceremony for a one-to-one relationship, and a join on the query that runs on every library
     * emission.
     */
    val ebookUri: String? = null,
    /**
     * Where the user stopped reading, as a spine index and a character offset into that spine
     * item's plain text (design D3). Both null until the ebook has been opened.
     *
     * Deliberately not a scroll offset in pixels: text size, line spacing, typeface, and rotation
     * all invalidate one of those, and this change adds controls for the first three.
     */
    val ebookSpineIndex: Int? = null,
    val ebookCharOffset: Int? = null,
    /**
     * A correction, in whole chapters, applied on top of the detected chapter-pairing offset
     * (design D3). Null means "use the detected offset unmodified" rather than zero, which would be
     * indistinguishable from a user-entered correction of zero.
     */
    val readAlongChapterOffset: Int? = null,
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

/**
 * One mark or note against a book (`add-notes-and-bookmarks` design D1).
 *
 * A bookmark and a note are the same record at different stages: [text] is null for a bare mark and
 * non-null once it has been written up. Annotating is therefore an update rather than a conversion
 * between two kinds of row, which is what makes mark-now-annotate-later — half the point of the
 * feature — cost nothing.
 *
 * Cascades from `audiobooks` exactly as [ChapterEntity] does. Notes are the only user-authored
 * content in this app, so that was a real decision rather than a default: the owner made it, and the
 * removal confirmation states the count before it takes them.
 */
@Entity(
    tableName = "notes",
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
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audiobookId: Long,
    /**
     * Where this note points, in raw Media3 player coordinates — the same pair, for the same
     * reasons, that [AudiobookEntity] stores its saved position in (design D2). It is the anchor,
     * already moved back by the lead-in, not the position playing when the user tapped.
     */
    val mediaItemIndex: Int,
    val positionMs: Long,
    /**
     * The title of the chapter [mediaItemIndex]/[positionMs] falls in, snapshotted when the note was
     * created (design D4).
     *
     * Denormalized on purpose. Deriving it from `chapters` at display time keeps one source of
     * truth, but a chapter index is only stable while the book's scan is: re-adding a folder book
     * after a file is renamed shifts every index, and every old note would then relabel itself
     * silently and plausibly. One string buys a list that survives a rescan.
     *
     * Of the *anchored* position, which for a mark taken just after a chapter boundary is the
     * previous chapter — the label has to agree with where the note actually seeks.
     */
    val chapterTitle: String,
    /** Null is a bare mark awaiting text; clearing a note's text returns it to that state. */
    val text: String? = null,
    val createdAt: Long,
)

/**
 * One owner-entered correction to the read-along correspondence, for one chapter of one book
 * (`add-readalong-nudge` design D1, D9).
 *
 * The pair is the whole record: at [audioMs] the narrator is at [charOffset] characters into the
 * ebook. Deliberately not a delta against what the map said at the time — a delta is only meaningful
 * relative to the map that produced it, and the map is rebuilt from scratch on every open. The pair
 * stays a true statement about the book however the chapter matching later changes.
 *
 * Keyed on the *audio* [chapterIndex], as [ChapterEntity] and [NoteEntity] are, because that is the
 * side that stays addressable. [charOffset] points into one specific EPUB, which is why relinking
 * discards these rows rather than carrying them over (design D10).
 */
@Entity(
    tableName = "read_along_corrections",
    primaryKeys = ["audiobookId", "chapterIndex"],
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
data class ReadAlongCorrectionEntity(
    val audiobookId: Long,
    val chapterIndex: Int,
    /** Absolute position in the audio, in the same book-wide milliseconds the map interpolates over. */
    val audioMs: Long,
    /** Absolute position in the ebook's text, in characters from the start of the book. */
    val charOffset: Int,
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
    /**
     * Whether this book has an ebook linked (`add-ebook-companion` design D16). A boolean rather
     * than the URI: the row needs to know *whether*, not *which*, and carrying a URI through a
     * query that runs on every library emission buys nothing.
     */
    val hasEbook: Boolean = false,
    /**
     * How many marks and notes this book carries. Not shown on the row — it exists so the removal
     * confirmation can state what it is about to destroy, notes being the only user-authored content
     * in the app (`add-notes-and-bookmarks` design D9).
     */
    val noteCount: Int = 0,
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
