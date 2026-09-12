package com.brandonmiller.audiobookplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_FOLDER
import com.brandonmiller.audiobookplayer.data.SOURCE_TYPE_M4B
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Loading a book must never let the saved position be observed as zero.
 *
 * `PlaybackService` writes progress from `onMediaItemTransition`, on a
 * `Dispatchers.Main.immediate` scope, so that write reads `currentPosition` *inline* — during the
 * `setMediaItems` call itself. Setting the playlist and then seeking afterwards therefore stored a
 * zero over the position being restored, while the player went on to seek and look perfectly
 * correct. The symptom was a book losing its place when it was opened and left without playing, and
 * nothing on screen showed it happening.
 *
 * These assert the position *as seen from inside the callback*, because that is the only moment the
 * bug existed. Asserting `player.currentPosition` after `loadBook` returns passes either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoadBookTest {

    private lateinit var player: ExoPlayer
    private val positionsSeenAtTransition = mutableListOf<Long>()

    @Before
    fun createPlayer() {
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    positionsSeenAtTransition += player.currentPosition
                }
            },
        )
    }

    @After
    fun releasePlayer() {
        player.release()
    }

    @Test
    fun `the saved position is already in place when the item transition fires`() {
        player.loadBook(bookAt(positionMs = 35_174_952), chapters(), fallbackSpeed = 1.0f)

        assertTrue("no item transition fired at all", positionsSeenAtTransition.isNotEmpty())
        assertEquals(
            "a transition observed position 0, which is what got written over the saved position",
            emptyList<Long>(),
            positionsSeenAtTransition.filter { it == 0L },
        )
        assertEquals(35_174_952L, positionsSeenAtTransition.first())
    }

    @Test
    fun `the player ends up at the saved position`() {
        player.loadBook(bookAt(positionMs = 35_174_952), chapters(), fallbackSpeed = 1.0f)

        assertEquals(35_174_952L, player.currentPosition)
        assertEquals(0, player.currentMediaItemIndex)
    }

    /** A book that has never been played starts at zero, and that zero is not a lost position. */
    @Test
    fun `a book with no saved position starts at the beginning`() {
        player.loadBook(bookAt(positionMs = null), chapters(), fallbackSpeed = 1.0f)

        assertEquals(0L, player.currentPosition)
        assertTrue(positionsSeenAtTransition.isNotEmpty())
    }

    /** A folder book restores into the right item, not just the right offset within item zero. */
    @Test
    fun `a saved position in a later item is restored into that item`() {
        // A folder book, so the playlist really has one item per chapter for the index to mean
        // anything — an `.m4b` collapses to a single item and the index is always zero.
        val book = bookAt(positionMs = 12_000, mediaItemIndex = 2, sourceType = SOURCE_TYPE_FOLDER)

        player.loadBook(book, folderChapters(), fallbackSpeed = 1.0f)

        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(emptyList<Long>(), positionsSeenAtTransition.filter { it == 0L })
    }

    /** A stored index that no longer exists must not be seeked to (the book was rescanned shorter). */
    @Test
    fun `a saved index beyond the playlist is ignored rather than crashing`() {
        val book = bookAt(positionMs = 12_000, mediaItemIndex = 99)

        player.loadBook(book, chapters(), fallbackSpeed = 1.0f)

        assertEquals(0, player.currentMediaItemIndex)
    }

    @Test
    fun `reopening the book already loaded does not reload it`() {
        val book = bookAt(positionMs = 35_174_952)
        player.loadBook(book, chapters(), fallbackSpeed = 1.0f)
        positionsSeenAtTransition.clear()

        val second = player.loadBook(book, chapters(), fallbackSpeed = 1.0f)

        assertEquals("a reload would restart the book", null, second)
        assertEquals(emptyList<Long>(), positionsSeenAtTransition)
    }

    private fun bookAt(
        positionMs: Long?,
        mediaItemIndex: Int = 0,
        sourceType: String = SOURCE_TYPE_M4B,
    ) = AudiobookEntity(
        id = 2,
        sourceUri = "content://doc/mistborn.m4b",
        sourceType = sourceType,
        title = "The Hero of Ages",
        addedAt = 1_000,
        lastMediaItemIndex = if (positionMs == null) null else mediaItemIndex,
        lastPositionMs = positionMs,
    )

    private fun chapters() = List(3) { index ->
        ChapterEntity(
            id = index.toLong(),
            audiobookId = 2,
            chapterIndex = index,
            title = "Chapter ${index + 1}",
            mediaUri = "content://doc/mistborn.m4b",
            startPositionMs = index * 600_000L,
            endPositionMs = (index + 1) * 600_000L,
        )
    }

    /** One file per chapter, which is what makes the media item index meaningful. */
    private fun folderChapters() = List(4) { index ->
        ChapterEntity(
            id = index.toLong(),
            audiobookId = 2,
            chapterIndex = index,
            title = "Track ${index + 1}",
            mediaUri = "content://tree/book/track$index.mp3",
            startPositionMs = 0,
        )
    }
}
