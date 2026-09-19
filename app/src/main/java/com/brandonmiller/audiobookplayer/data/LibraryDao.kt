package com.brandonmiller.audiobookplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    /**
     * The library list, with the two derived figures the redesign shows on every row and on the
     * resume card.
     *
     * `durationMs` is all-or-nothing: `COUNT(c.endPositionMs)` skips nulls, so it equals
     * `COUNT(c.id)` only when every chapter's end is known, and the total is null otherwise. A
     * partially resolved book reports no duration rather than an understated one (design D3).
     *
     * `positionMs` converts the stored player coordinates — `lastMediaItemIndex` plus
     * `lastPositionMs` — into a book-wide position by adding the lengths of the chapters before
     * the current one. The one expression covers both book shapes: an `.m4b` keeps every chapter
     * at media item 0, so nothing matches `chapterIndex < 0` and `lastPositionMs` is already
     * absolute, while a folder book's chapter and media-item indices are equal, so the subquery
     * sums exactly what precedes it (design D4).
     *
     * `noteCount` is a correlated subquery and **must not** become a second `LEFT JOIN`. This query
     * aggregates over the chapter join, so joining a second one-to-many table would multiply the
     * rows and silently inflate both `chapterCount` and `durationMs`. The subquery is cheap against
     * `index_notes_audiobookId`, and it is here rather than fetched when the removal sheet opens so
     * that the confirmation states the count as part of the row it already has
     * (`add-notes-and-bookmarks` design D9).
     */
    @Query(
        """
        SELECT a.id AS id, a.title AS title, a.sourceUri AS sourceUri,
               COUNT(c.id) AS chapterCount, a.artworkPath AS artworkPath,
               CASE WHEN COUNT(c.id) > 0 AND COUNT(c.id) = COUNT(c.endPositionMs)
                    THEN SUM(c.endPositionMs - c.startPositionMs)
               END AS durationMs,
               (
                   SELECT COALESCE(SUM(p.endPositionMs - p.startPositionMs), 0)
                   FROM chapters p
                   WHERE p.audiobookId = a.id AND p.chapterIndex < a.lastMediaItemIndex
               ) + a.lastPositionMs AS positionMs,
               a.lastPlayedAt AS lastPlayedAt,
               a.ebookUri IS NOT NULL AS hasEbook,
               (SELECT COUNT(*) FROM notes n WHERE n.audiobookId = a.id) AS noteCount
        FROM audiobooks a
        LEFT JOIN chapters c ON c.audiobookId = a.id
        GROUP BY a.id
        ORDER BY a.addedAt DESC
        """,
    )
    fun observeLibrary(): Flow<List<LibraryBook>>

    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    suspend fun findBook(audiobookId: Long): AudiobookEntity?

    /**
     * Observed rather than read once, so the Player's ebook icon is right after the Reader unlinks.
     * Both screens outlive each other on the back stack, and a one-shot read leaves the icon
     * claiming a link that no longer exists until the Player is rebuilt.
     */
    @Query("SELECT ebookUri FROM audiobooks WHERE id = :audiobookId")
    fun observeEbookUri(audiobookId: Long): Flow<String?>

    @Query("SELECT * FROM chapters WHERE audiobookId = :audiobookId ORDER BY chapterIndex ASC")
    suspend fun chaptersFor(audiobookId: Long): List<ChapterEntity>

    @Insert
    suspend fun insertBook(book: AudiobookEntity): Long

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    /**
     * A book and its chapters land together or not at all, so a failure partway through a
     * 128-file scan cannot leave a half-built book in the library.
     */
    @Transaction
    suspend fun insertBookWithChapters(book: AudiobookEntity, chapters: List<ChapterEntity>): Long {
        val bookId = insertBook(book)
        insertChapters(chapters.map { it.copy(audiobookId = bookId) })
        return bookId
    }

    /** Chapters cascade. This removes the app's record only — never the user's files. */
    @Query("DELETE FROM audiobooks WHERE id = :audiobookId")
    suspend fun deleteBook(audiobookId: Long)

    @Query(
        """
        UPDATE audiobooks
        SET lastMediaItemIndex = :mediaItemIndex, lastPositionMs = :positionMs, lastPlayedAt = :playedAt
        WHERE id = :audiobookId
        """,
    )
    suspend fun updateProgress(audiobookId: Long, mediaItemIndex: Int, positionMs: Long, playedAt: Long)

    @Query("UPDATE audiobooks SET playbackSpeed = :speed WHERE id = :audiobookId")
    suspend fun updateSpeed(audiobookId: Long, speed: Float)

    /**
     * Written after the book itself, because the cover file is named for the book id and there is
     * no id until the insert has happened. A book with no cover simply never gets this call.
     */
    @Query("UPDATE audiobooks SET artworkPath = :path WHERE id = :audiobookId")
    suspend fun updateArtworkPath(audiobookId: Long, path: String)

    /**
     * Records a folder chapter's length, learned when the Player resolved it in the background.
     *
     * A folder chapter starts at zero within its own file, so its end is its duration — the column
     * means the same thing here as it does for an `.m4b`, whose ends are parsed at add time
     * instead. Storing what the Player already read is what lets the library show a folder book's
     * total without opening up to 128 files while the user waits (design D3).
     *
     * Guarded on the column still being null so a resolution pass cannot overwrite an `.m4b`'s
     * exact, parsed boundary with a figure read back from the container.
     */
    @Query("UPDATE chapters SET endPositionMs = :endPositionMs WHERE id = :chapterId AND endPositionMs IS NULL")
    suspend fun updateChapterEnd(chapterId: Long, endPositionMs: Long)

    // ---------------------------------------------------------------- ebook companion

    /**
     * Links an ebook, discarding whatever reading position was saved.
     *
     * Clearing is the point of doing both in one statement: a position is an offset into a
     * particular book's text, so carrying it across to a different ebook would open the new one at
     * an arbitrary place that looks deliberate.
     */
    @Query(
        """
        UPDATE audiobooks
        SET ebookUri = :ebookUri, ebookSpineIndex = NULL, ebookCharOffset = NULL
        WHERE id = :audiobookId
        """,
    )
    suspend fun linkEbook(audiobookId: Long, ebookUri: String)

    /** Removes the app's record of the ebook. Never touches the file, as with removing a book. */
    @Query(
        """
        UPDATE audiobooks
        SET ebookUri = NULL, ebookSpineIndex = NULL, ebookCharOffset = NULL
        WHERE id = :audiobookId
        """,
    )
    suspend fun unlinkEbook(audiobookId: Long)

    @Query(
        """
        UPDATE audiobooks
        SET ebookSpineIndex = :spineIndex, ebookCharOffset = :charOffset
        WHERE id = :audiobookId
        """,
    )
    suspend fun updateReadingPosition(audiobookId: Long, spineIndex: Int, charOffset: Int)

    // ---------------------------------------------------------------- read-along

    @Query("UPDATE audiobooks SET readAlongChapterOffset = :offset WHERE id = :audiobookId")
    suspend fun updateReadAlongChapterOffset(audiobookId: Long, offset: Int)

    /**
     * The owner's corrections for one book (`add-readalong-nudge` design D1). Ordered by position so
     * they drop into the anchor list the map interpolates over without a second sort.
     *
     * Read once when the reader builds the map rather than observed: the reader is the only thing
     * that writes them, and it already holds the map it just rebuilt.
     */
    @Query("SELECT * FROM read_along_corrections WHERE audiobookId = :audiobookId ORDER BY audioMs ASC")
    suspend fun readAlongCorrections(audiobookId: Long): List<ReadAlongCorrectionEntity>

    /**
     * Records a correction, replacing any the same chapter already carried (design D7).
     *
     * [OnConflictStrategy.REPLACE] against the composite primary key is where replace-don't-
     * accumulate actually lives. The UI never has to look for an existing row, and no path through
     * it can leave a chapter carrying two corrections that disagree.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadAlongCorrection(correction: ReadAlongCorrectionEntity)

    /**
     * Discards every correction for a book, for when the linked ebook is replaced or unlinked
     * (design D10). A correction addresses a character offset into one specific EPUB; against a
     * different file it points somewhere arbitrary.
     */
    @Query("DELETE FROM read_along_corrections WHERE audiobookId = :audiobookId")
    suspend fun clearReadAlongCorrections(audiobookId: Long)

    // ---------------------------------------------------------------- notes and bookmarks

    /**
     * One book's marks and notes, in book order rather than the order they were taken
     * (`add-notes-and-bookmarks` design D11). Book order is discussion order; chronological order
     * would interleave a re-listen with a first pass and read as noise.
     *
     * Ordering by the stored anchor works for both book shapes without translating it: a folder
     * book's notes sort by media item and then within it, and an `.m4b`'s all share item 0 and sort
     * by position alone, which is already the absolute one.
     */
    @Query("SELECT * FROM notes WHERE audiobookId = :audiobookId ORDER BY mediaItemIndex ASC, positionMs ASC")
    fun observeNotes(audiobookId: Long): Flow<List<NoteEntity>>

    /** Returns the new note's id, which is what lets the Player offer to annotate what it just took. */
    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    /**
     * Null clears the text and leaves a bare mark rather than deleting the row (design D1) — the
     * same record at an earlier stage, not a different kind of thing.
     */
    @Query("UPDATE notes SET `text` = :text WHERE id = :noteId")
    suspend fun updateNoteText(noteId: Long, text: String?)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    // ---------------------------------------------------------------- chapter summaries

    /**
     * One book's summaries, in chapter order (`add-chapter-summaries` design D2).
     *
     * Observed rather than read once, unlike the read-along corrections above: an import replaces the
     * whole set while the chapter sheet that triggered it is still open, and the controls on its rows
     * have to appear without the sheet being dismissed and reopened.
     */
    @Query("SELECT * FROM chapter_summaries WHERE audiobookId = :audiobookId ORDER BY chapterIndex ASC")
    fun observeChapterSummaries(audiobookId: Long): Flow<List<ChapterSummaryEntity>>

    @Query("SELECT * FROM chapter_summaries WHERE audiobookId = :audiobookId ORDER BY chapterIndex ASC")
    suspend fun chapterSummaries(audiobookId: Long): List<ChapterSummaryEntity>

    /**
     * One chapter's summary, or null when it has none. Read at a chapter boundary to decide whether
     * there is anything to offer, which is why it fetches the row rather than reading the observed
     * list: the observed list carries the text but not [ChapterSummaryEntity.prompted].
     */
    @Query("SELECT * FROM chapter_summaries WHERE audiobookId = :audiobookId AND chapterIndex = :chapterIndex")
    suspend fun chapterSummary(audiobookId: Long, chapterIndex: Int): ChapterSummaryEntity?

    @Query("DELETE FROM chapter_summaries WHERE audiobookId = :audiobookId")
    suspend fun clearChapterSummaries(audiobookId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterSummaries(summaries: List<ChapterSummaryEntity>)

    /**
     * Marks a chapter as having had its end-of-chapter offer made (design D6).
     *
     * Deliberately not an upsert of the whole row: the only thing that legitimately changes here is
     * the flag, and writing the row back would let a stale copy of [ChapterSummaryEntity.text] held
     * by the Player overwrite a summary a re-import had replaced in between.
     */
    @Query("UPDATE chapter_summaries SET prompted = 1 WHERE audiobookId = :audiobookId AND chapterIndex = :chapterIndex")
    suspend fun markChapterSummaryPrompted(audiobookId: Long, chapterIndex: Int)

    /**
     * Replaces every summary a book carries with the imported set, in one transaction (design D5).
     *
     * Replace rather than merge, because editing the file and importing it again is the only editing
     * path this feature has: an entry the owner deleted from the file has to disappear from the book
     * rather than survive from the previous import. The delete and the insert are one transaction so
     * that a failure partway cannot leave a book with neither its old summaries nor its new ones.
     */
    @Transaction
    suspend fun replaceChapterSummaries(audiobookId: Long, summaries: List<ChapterSummaryEntity>) {
        clearChapterSummaries(audiobookId)
        insertChapterSummaries(summaries)
    }
}
