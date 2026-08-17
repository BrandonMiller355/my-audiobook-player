package com.brandonmiller.audiobookplayer.data

import androidx.room.Dao
import androidx.room.Insert
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
               a.lastPlayedAt AS lastPlayedAt
        FROM audiobooks a
        LEFT JOIN chapters c ON c.audiobookId = a.id
        GROUP BY a.id
        ORDER BY a.addedAt DESC
        """,
    )
    fun observeLibrary(): Flow<List<LibraryBook>>

    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    suspend fun findBook(audiobookId: Long): AudiobookEntity?

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
}
