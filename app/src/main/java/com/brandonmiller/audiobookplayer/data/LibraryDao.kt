package com.brandonmiller.audiobookplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT a.id AS id, a.title AS title, a.sourceUri AS sourceUri,
               COUNT(c.id) AS chapterCount, a.artworkPath AS artworkPath
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
}
