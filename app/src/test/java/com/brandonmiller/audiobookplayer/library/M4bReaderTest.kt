package com.brandonmiller.audiobookplayer.library

import com.brandonmiller.audiobookplayer.m4b.Chapter
import com.brandonmiller.audiobookplayer.m4b.ChapterParseResult
import com.brandonmiller.audiobookplayer.m4b.M4bChapterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Covers the mapping from the parser's three outcomes to a book, which is where design D5 lives:
 * unchaptered and unreadable produce the same one-chapter book and differ only in what is said.
 *
 * This is the level the chaptered path can be verified at at all — every `.m4b` in the owner's
 * library parses as unchaptered, so no device check can reach the other branch.
 */
class M4bReaderTest {

    @Test
    fun `a chaptered file keeps every mark and takes its duration from the last end`() {
        val chapters = listOf(
            Chapter(index = 0, title = "Prologue", startMs = 0, endMs = 600_000),
            Chapter(index = 1, title = "Chapter 1", startMs = 600_000, endMs = 1_500_000),
            Chapter(index = 2, title = "Chapter 2", startMs = 1_500_000, endMs = 2_400_000),
        )

        val contents = contentsFrom(
            title = "Mistborn",
            parseResult = ChapterParseResult.Chapters(chapters),
            // Deliberately wrong: a chaptered file's duration comes from its own last chapter end,
            // not from a separate read (design D2).
            fileDurationMs = 99L,
        )

        assertEquals(chapters, contents.chapters)
        assertEquals(2_400_000L, contents.durationMs)
        assertFalse(contents.chaptersUnreadable)
    }

    @Test
    fun `an unchaptered file becomes one chapter spanning the whole file, silently`() {
        val contents = contentsFrom(
            title = "Mistborn",
            parseResult = ChapterParseResult.Unchaptered,
            fileDurationMs = 46_800_000,
        )

        assertEquals(listOf(Chapter(0, "Mistborn", 0, 46_800_000)), contents.chapters)
        assertEquals(46_800_000L, contents.durationMs)
        assertFalse("no chapter marks is a normal state, not a failure", contents.chaptersUnreadable)
    }

    @Test
    fun `an unreadable file becomes the same book, and only the message differs`() {
        val duration = 46_800_000L
        val unchaptered = contentsFrom("Mistborn", ChapterParseResult.Unchaptered, duration)
        val unreadable = contentsFrom("Mistborn", ChapterParseResult.Unreadable("malformed chpl"), duration)

        assertEquals(unchaptered.chapters, unreadable.chapters)
        assertEquals(unchaptered.durationMs, unreadable.durationMs)
        assertTrue("the user is told the chapters could not be read", unreadable.chaptersUnreadable)
    }

    @Test
    fun `a file the provider cannot seek is still a playable one-chapter book`() {
        val parseResult = M4bChapterParser.parse(UnseekableChannel(unchapteredMp4(durationMs = 3_600_000)))
        assertTrue(
            "the parser reports a non-seekable source as unreadable",
            parseResult is ChapterParseResult.Unreadable,
        )

        val contents = contentsFrom("Mistborn", parseResult, fileDurationMs = 3_600_000)

        assertEquals(1, contents.chapters.size)
        assertEquals(3_600_000L, contents.chapters.single().endMs)
        assertTrue(contents.chaptersUnreadable)
    }

    @Test
    fun `a seekable file with no chapter marks parses and maps without a message`() {
        val parseResult = M4bChapterParser.parse(InMemoryChannel(unchapteredMp4(durationMs = 3_600_000)))
        assertEquals(ChapterParseResult.Unchaptered, parseResult)

        assertFalse(contentsFrom("Mistborn", parseResult, 3_600_000).chaptersUnreadable)
    }

    @Test
    fun `an unknown duration leaves the single chapter's end unknown rather than guessed`() {
        val contents = contentsFrom("Mistborn", ChapterParseResult.Unchaptered, fileDurationMs = null)

        assertEquals(0L, contents.durationMs)
        assertEquals(0L, contents.chapters.single().endMs)
    }

    @Test
    fun `the title is the file name without its extension`() {
        assertEquals("Mistborn - The Final Empire", titleFrom("Mistborn - The Final Empire.m4b"))
        assertEquals("Book 1.5", titleFrom("Book 1.5.M4B"))
        assertEquals("noextension", titleFrom("noextension"))
        assertEquals("Audiobook", titleFrom(""))
    }

    // ---------------------------------------------------------------- fixtures

    /** The smallest thing the parser accepts as an MP4: an `ftyp` and a `moov` holding an `mvhd`. */
    private fun unchapteredMp4(durationMs: Long): ByteArray =
        box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII), u32(0), "M4A mp42".toByteArray(Charsets.US_ASCII)) +
            box("moov", box("mvhd", u32(0), u32(0), u32(0), u32(1000), u32(durationMs)))

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val payload = parts.fold(ByteArray(0)) { accumulated, part -> accumulated + part }
        return u32((payload.size + 8).toLong()) + type.toByteArray(Charsets.US_ASCII) + payload
    }

    private fun u32(value: Long) = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private open class InMemoryChannel(private val data: ByteArray) : SeekableByteChannel {
        private var position = 0L
        private var open = true

        override fun read(dst: ByteBuffer): Int {
            if (position >= data.size) return -1
            val count = minOf(dst.remaining().toLong(), data.size - position).toInt()
            dst.put(data, position.toInt(), count)
            position += count
            return count
        }

        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException("read-only")
        override fun position(): Long = position
        override fun position(newPosition: Long): SeekableByteChannel = apply { position = newPosition }
        override fun size(): Long = data.size.toLong()
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException("read-only")
        override fun isOpen(): Boolean = open
        override fun close() { open = false }
    }

    /** Stands in for a document provider that hands back a pipe rather than a real file (design D8). */
    private class UnseekableChannel(data: ByteArray) : InMemoryChannel(data) {
        override fun position(newPosition: Long): SeekableByteChannel =
            throw IOException("this document provider does not support seeking")
    }
}
