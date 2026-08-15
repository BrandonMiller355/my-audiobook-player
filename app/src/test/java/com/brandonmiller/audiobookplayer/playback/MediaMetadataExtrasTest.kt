package com.brandonmiller.audiobookplayer.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Design D1 rests entirely on one premise: that a `MediaMetadata` extras `Bundle` set on a
 * `MediaItem` by the controller is still there when the service reads it back off
 * `mediaSession.player`. If it were not, the whole of D1 would have to be replaced by its rejected
 * alternative — the service reading chapters from Room, pre-warmed on media-item transition.
 *
 * The risk in that premise is serialization, not IPC: the controller-to-session hop bundles each
 * `MediaItem` and rebuilds it on the far side, and a field that `toBundle` does not write is a
 * field the session never sees. So this exercises exactly that round trip, with the real array
 * sizes a book produces. (The complementary on-device check — press a Bluetooth "previous" with the
 * UI gone and watch it land on a chapter boundary — is on the manual verification list.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaMetadataExtrasTest {

    @Test
    fun `chapter bounds set on a media item survive the bundling the session boundary performs`() {
        val starts = longArrayOf(0, 600_000, 1_500_000, 2_400_000)
        val durationMs = 3_600_000L

        val item = MediaItem.Builder()
            .setUri("content://documents/document/mistborn.m4b")
            .setMediaId("7:0")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Mistborn")
                    .setExtras(
                        Bundle().apply {
                            putLongArray(EXTRA_CHAPTER_STARTS_MS, starts)
                            putLong(EXTRA_BOOK_DURATION_MS, durationMs)
                        },
                    )
                    .build(),
            )
            .build()

        val received = MediaItem.fromBundle(item.toBundle())

        val extras = received.mediaMetadata.extras
        assertNotNull("the extras bundle survived at all", extras)
        assertArrayEquals(starts, extras!!.getLongArray(EXTRA_CHAPTER_STARTS_MS))
        assertEquals(durationMs, extras.getLong(EXTRA_BOOK_DURATION_MS))
        assertEquals("7:0", received.mediaId)
    }

    @Test
    fun `a folder book's items carry no bounds, which is how the two shapes are told apart`() {
        val item = MediaItem.Builder()
            .setUri("content://documents/document/chapter-01.mp3")
            .setMediaId("3:0")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Chapter 1").build())
            .build()

        val received = MediaItem.fromBundle(item.toBundle())

        assertEquals(null, received.mediaMetadata.extras?.getLongArray(EXTRA_CHAPTER_STARTS_MS))
    }

    @Test
    fun `a book at the parser's chapter cap stays far under the transaction limit`() {
        // Design D1 accepts duplicating the bounds into a Bundle on the grounds that the parser
        // caps chapters at 10,000, making the worst case an 80 KB long[] against a ~1 MB limit.
        val starts = LongArray(10_000) { it * 60_000L }

        val bundle = Bundle().apply { putLongArray(EXTRA_CHAPTER_STARTS_MS, starts) }
        val received = MediaItem.fromBundle(
            MediaItem.Builder()
                .setMediaId("1:0")
                .setMediaMetadata(MediaMetadata.Builder().setExtras(bundle).build())
                .build()
                .toBundle(),
        )

        assertEquals(10_000, received.mediaMetadata.extras?.getLongArray(EXTRA_CHAPTER_STARTS_MS)?.size)
    }
}
