package com.brandonmiller.audiobookplayer.m4b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Fixtures are built here rather than checked in as binaries: no chaptered `.m4b` is available,
 * and synthetic bytes can express the 64-bit, truncated, and hostile-count cases that a real
 * file never would.
 */
class M4bChapterParserTest {

    // ------------------------------------------------------------------ Nero chpl

    @Test
    fun `chpl chapters are read with titles and start times`() {
        val file = fileWithChpl(
            durationMs = 200_000,
            marks = listOf(0L to "Prologue", 60_000L to "Chapter One", 120_000L to "Chapter Two"),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(3, chapters.size)
        assertEquals(listOf("Prologue", "Chapter One", "Chapter Two"), chapters.map { it.title })
        assertEquals(listOf(0L, 60_000L, 120_000L), chapters.map { it.startMs })
    }

    @Test
    fun `chpl declaring more entries than it holds stops at the last real one`() {
        val entries = neroEntry(0L, "One") + neroEntry(60_000L, "Two")
        val chpl = box("chpl", u32(0x01000000), u32(0), byteArrayOf(200.toByte()), entries)
        val file = assemble(
            moovExtras = box("udta", chpl),
            durationMs = 200_000,
        )

        val result = M4bChapterParser.parse(channel(file))

        assertEquals(2, (result as ChapterParseResult.Chapters).chapters.size)
    }

    // ------------------------------------------------------------------ QuickTime chapter track

    @Test
    fun `quicktime chapter track is read with titles and start times`() {
        val file = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 400_000,
            samples = listOf(
                60_000L to "One",
                60_000L to "Two",
                60_000L to "Three",
                220_000L to "Four",
            ),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(4, chapters.size)
        assertEquals(listOf("One", "Two", "Three", "Four"), chapters.map { it.title })
        assertEquals(listOf(0L, 60_000L, 120_000L, 180_000L), chapters.map { it.startMs })
    }

    @Test
    fun `chapter track with 64-bit chunk offsets is read`() {
        val file = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 200_000,
            samples = listOf(60_000L to "One", 140_000L to "Two"),
            useCo64 = true,
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf("One", "Two"), chapters.map { it.title })
    }

    @Test
    fun `chap reference to a non-text track yields no chapters`() {
        // Mirrors real files, which reference a 'vide' thumbnail track alongside the text one —
        // except here the text track is absent entirely.
        val file = assemble(
            traks = audioTrak(trackId = 1, chapRefs = listOf(2L)) + trak(
                trackId = 2,
                handler = "vide",
                timescale = 1000,
                stbl = ByteArray(0),
            ),
            durationMs = 200_000,
        )

        assertEquals(ChapterParseResult.Unchaptered, M4bChapterParser.parse(channel(file)))
    }

    @Test
    fun `quicktime track wins when both encodings are present`() {
        val chpl = box(
            "chpl",
            u32(0x01000000), u32(0), byteArrayOf(2),
            neroEntry(0L, "nero one") + neroEntry(100_000L, "nero two"),
        )
        val file = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 300_000,
            samples = listOf(60_000L to "track one", 60_000L to "track two", 180_000L to "track three"),
            moovExtras = box("udta", chpl),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf("track one", "track two", "track three"), chapters.map { it.title })
    }

    // ------------------------------------------------------------------ the unchaptered rule

    @Test
    fun `single sample spanning the whole file is unchaptered whatever its title says`() {
        // This is the exact shape of every .m4b in the owner's library.
        for (title in listOf("Brandon Sanderson - Mistborn 03 - The Hero of Ages", "Chapter 1", "")) {
            val file = fileWithChapterTrack(
                timescale = 600,
                durationMs = 49_327_000,
                samples = listOf(49_327_000L to title),
            )

            assertEquals(
                "title '$title' should not affect the outcome",
                ChapterParseResult.Unchaptered,
                M4bChapterParser.parse(channel(file)),
            )
        }
    }

    @Test
    fun `single whole-file mark starting slightly after zero is unchaptered`() {
        val file = fileWithChpl(durationMs = 200_000, marks = listOf(400L to "The Book"))

        assertEquals(ChapterParseResult.Unchaptered, M4bChapterParser.parse(channel(file)))
    }

    @Test
    fun `two genuine marks are not collapsed`() {
        val file = fileWithChpl(
            durationMs = 200_000,
            marks = listOf(0L to "One", 100_000L to "Two"),
        )

        assertEquals(2, (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters.size)
    }

    // ------------------------------------------------------------------ boundaries

    @Test
    fun `ends are chained from the next start and the last uses the movie duration`() {
        val file = fileWithChpl(
            durationMs = 200_000,
            marks = listOf(0L to "a", 60_000L to "b", 120_000L to "c"),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf(60_000L, 120_000L, 200_000L), chapters.map { it.endMs })
        assertEquals(listOf(0, 1, 2), chapters.map { it.index })
    }

    @Test
    fun `duplicate and out-of-order marks are ordered and never zero length`() {
        val file = fileWithChpl(
            durationMs = 200_000,
            marks = listOf(120_000L to "third", 0L to "first", 60_000L to "second", 60_000L to "duplicate"),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf(0L, 60_000L, 120_000L), chapters.map { it.startMs })
        assertEquals(listOf("first", "second", "third"), chapters.map { it.title })
        assertTrue(chapters.all { it.durationMs > 0 })
    }

    @Test
    fun `marks beyond the movie duration are discarded`() {
        val file = fileWithChpl(
            durationMs = 200_000,
            marks = listOf(0L to "kept", 100_000L to "also kept", 500_000L to "past the end"),
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf("kept", "also kept"), chapters.map { it.title })
        assertEquals(200_000L, chapters.last().endMs)
    }

    // ------------------------------------------------------------------ absence and failure

    @Test
    fun `file with no chapter data at all is unchaptered, not an error`() {
        val file = assemble(traks = audioTrak(trackId = 1, chapRefs = emptyList()), durationMs = 200_000)

        assertEquals(ChapterParseResult.Unchaptered, M4bChapterParser.parse(channel(file)))
    }

    @Test
    fun `bytes that are not an mp4 are unreadable`() {
        val notAnMp4 = ByteArray(4096) { (it * 31 % 251).toByte() }

        assertTrue(M4bChapterParser.parse(channel(notAnMp4)) is ChapterParseResult.Unreadable)
    }

    @Test
    fun `empty input is unreadable`() {
        assertTrue(M4bChapterParser.parse(channel(ByteArray(0))) is ChapterParseResult.Unreadable)
    }

    @Test
    fun `truncated file terminates without throwing`() {
        val full = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 200_000,
            samples = listOf(60_000L to "One", 140_000L to "Two"),
        )
        val truncated = full.copyOf(full.size * 6 / 10)

        val result = M4bChapterParser.parse(channel(truncated))

        assertTrue(result is ChapterParseResult.Unreadable || result is ChapterParseResult.Chapters)
    }

    @Test
    fun `box declaring a size smaller than its header terminates iteration`() {
        val broken = u32(4) + "junk".toByteArray(Charsets.US_ASCII)
        val file = ftyp() + broken + assemble(durationMs = 200_000).drop(ftyp().size).toByteArray()

        val result = M4bChapterParser.parse(channel(file))

        assertTrue(result is ChapterParseResult.Unreadable)
    }

    @Test
    fun `channel that cannot seek is reported as unreadable`() {
        val file = fileWithChpl(durationMs = 200_000, marks = listOf(0L to "a", 60_000L to "b"))

        val result = M4bChapterParser.parse(UnseekableChannel(file))

        assertTrue(result is ChapterParseResult.Unreadable)
    }

    @Test
    fun `sample table declaring a hostile entry count does not exhaust memory`() {
        val stbl = box(
            "stbl",
            box("stts", u32(0), u32(0xFFFFFFFFL), u32(1), u32(1000)),
            box("stsz", u32(0), u32(0), u32(0xFFFFFFFFL)),
            box("stsc", u32(0), u32(0xFFFFFFFFL), u32(1), u32(1), u32(1)),
            box("stco", u32(0), u32(0xFFFFFFFFL), u32(0)),
        )
        val file = assemble(
            traks = audioTrak(trackId = 1, chapRefs = listOf(2L)) +
                trak(trackId = 2, handler = "text", timescale = 1000, stbl = stbl),
            durationMs = 200_000,
        )

        val result = M4bChapterParser.parse(channel(file))

        assertTrue(result is ChapterParseResult.Unchaptered || result is ChapterParseResult.Chapters)
    }

    // ------------------------------------------------------------------ layout and read volume

    @Test
    fun `chapters are found when moov sits after mdat`() {
        val file = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 200_000,
            samples = listOf(60_000L to "One", 140_000L to "Two"),
            moovFirst = false,
        )

        val chapters = (M4bChapterParser.parse(channel(file)) as ChapterParseResult.Chapters).chapters

        assertEquals(listOf("One", "Two"), chapters.map { it.title })
    }

    @Test
    fun `parsing reads only metadata, not the audio payload or the audio sample tables`() {
        // An audio track with a large sample table, and a megabyte of audio payload. Neither
        // should be read: real files carry a 6 MB stsz that must never be expanded.
        val bigAudioStbl = box(
            "stbl",
            box("stsz", u32(0), u32(0), u32(20_000), ByteArray(20_000 * 4)),
            box("stco", u32(0), u32(5_000), ByteArray(5_000 * 4)),
        )
        val file = fileWithChapterTrack(
            timescale = 1000,
            durationMs = 200_000,
            samples = listOf(60_000L to "One", 140_000L to "Two"),
            audioStbl = bigAudioStbl,
            padBytes = 1_000_000,
        )

        val counting = CountingChannel(file)
        val chapters = (M4bChapterParser.parse(counting) as ChapterParseResult.Chapters).chapters

        assertEquals(2, chapters.size)
        assertTrue(
            "read ${counting.bytesRead} bytes of a ${file.size}-byte file",
            counting.bytesRead < 32_000,
        )
    }

    // ================================================================== fixtures

    private fun fileWithChpl(durationMs: Long, marks: List<Pair<Long, String>>): ByteArray {
        val entries = marks.fold(ByteArray(0)) { acc, (startMs, title) -> acc + neroEntry(startMs, title) }
        val chpl = box("chpl", u32(0x01000000), u32(0), byteArrayOf(marks.size.toByte()), entries)
        return assemble(moovExtras = box("udta", chpl), durationMs = durationMs)
    }

    /** [samples] pairs each sample's duration in track ticks with its title. */
    private fun fileWithChapterTrack(
        timescale: Long,
        durationMs: Long,
        samples: List<Pair<Long, String>>,
        useCo64: Boolean = false,
        moovFirst: Boolean = true,
        moovExtras: ByteArray = ByteArray(0),
        audioStbl: ByteArray = ByteArray(0),
        padBytes: Int = 0,
    ): ByteArray {
        val payloads = samples.map { (_, title) ->
            val bytes = title.toByteArray(Charsets.UTF_8)
            u16(bytes.size) + bytes
        }
        val mdatPayload = payloads.fold(ByteArray(0)) { acc, p -> acc + p } + ByteArray(padBytes)

        fun moovFor(mdatPayloadOffset: Long): ByteArray {
            val sttsEntries = samples.fold(ByteArray(0)) { acc, (delta, _) -> acc + u32(1) + u32(delta) }
            val stbl = box(
                "stbl",
                box("stts", u32(0), u32(samples.size.toLong()), sttsEntries),
                box(
                    "stsz",
                    u32(0), u32(0), u32(samples.size.toLong()),
                    payloads.fold(ByteArray(0)) { acc, p -> acc + u32(p.size.toLong()) },
                ),
                box("stsc", u32(0), u32(1), u32(1), u32(samples.size.toLong()), u32(1)),
                if (useCo64) {
                    box("co64", u32(0), u32(1), u64(mdatPayloadOffset))
                } else {
                    box("stco", u32(0), u32(1), u32(mdatPayloadOffset))
                },
            )
            return box(
                "moov",
                mvhd(timescale = 1000, durationMs = durationMs),
                audioTrak(trackId = 1, chapRefs = listOf(2L), stbl = audioStbl),
                trak(trackId = 2, handler = "text", timescale = timescale, stbl = stbl),
                moovExtras,
            )
        }

        val header = ftyp()
        return if (moovFirst) {
            val moovSize = moovFor(0).size
            val mdatPayloadOffset = header.size + moovSize + 8L
            header + moovFor(mdatPayloadOffset) + box("mdat", mdatPayload)
        } else {
            val mdatPayloadOffset = header.size + 8L
            header + box("mdat", mdatPayload) + moovFor(mdatPayloadOffset)
        }
    }

    private fun assemble(
        traks: ByteArray = audioTrak(trackId = 1, chapRefs = emptyList()),
        moovExtras: ByteArray = ByteArray(0),
        durationMs: Long,
    ): ByteArray = ftyp() + box(
        "moov",
        mvhd(timescale = 1000, durationMs = durationMs),
        traks,
        moovExtras,
    )

    private fun ftyp() = box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII), u32(0), "M4A mp42".toByteArray(Charsets.US_ASCII))

    private fun mvhd(timescale: Long, durationMs: Long) = box(
        "mvhd",
        u32(0), u32(0), u32(0),
        u32(timescale),
        u32(durationMs * timescale / 1000),
    )

    private fun audioTrak(trackId: Long, chapRefs: List<Long>, stbl: ByteArray = ByteArray(0)): ByteArray {
        val tref = if (chapRefs.isEmpty()) {
            ByteArray(0)
        } else {
            box("tref", box("chap", chapRefs.fold(ByteArray(0)) { acc, id -> acc + u32(id) }))
        }
        return box(
            "trak",
            tkhd(trackId),
            tref,
            box("mdia", mdhd(1000), hdlr("soun"), box("minf", box("stbl", stbl))),
        )
    }

    private fun trak(trackId: Long, handler: String, timescale: Long, stbl: ByteArray): ByteArray = box(
        "trak",
        tkhd(trackId),
        box("mdia", mdhd(timescale), hdlr(handler), box("minf", stbl.wrapAsStbl())),
    )

    private fun ByteArray.wrapAsStbl(): ByteArray = if (isEmpty()) box("stbl") else this

    private fun tkhd(trackId: Long) = box("tkhd", u32(0), u32(0), u32(0), u32(trackId), u32(0), u32(0))

    private fun mdhd(timescale: Long) = box("mdhd", u32(0), u32(0), u32(0), u32(timescale), u32(0), u32(0))

    private fun hdlr(handler: String) = box(
        "hdlr",
        u32(0), u32(0),
        handler.toByteArray(Charsets.US_ASCII),
        u32(0), u32(0), u32(0),
        byteArrayOf(0),
    )

    private fun neroEntry(startMs: Long, title: String): ByteArray {
        val bytes = title.toByteArray(Charsets.UTF_8)
        return u64(startMs * 10_000) + byteArrayOf(bytes.size.toByte()) + bytes
    }

    // ---------------------------------------------------------------- byte helpers

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val payload = parts.fold(ByteArray(0)) { acc, part -> acc + part }
        return u32((payload.size + 8).toLong()) + type.toByteArray(Charsets.US_ASCII) + payload
    }

    private fun u16(value: Int) = byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private fun u32(value: Long) = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun u64(value: Long) = u32((value ushr 32) and 0xFFFFFFFFL) + u32(value and 0xFFFFFFFFL)

    // ---------------------------------------------------------------- channels

    private fun channel(bytes: ByteArray): SeekableByteChannel = InMemoryChannel(bytes)

    /** Minimal in-memory [SeekableByteChannel] so the parser can run without a file on disk. */
    private open class InMemoryChannel(protected val data: ByteArray) : SeekableByteChannel {
        private var position = 0L
        private var open = true

        override fun read(dst: ByteBuffer): Int {
            if (position >= data.size) return -1
            val count = minOf(dst.remaining().toLong(), data.size - position).toInt()
            dst.put(data, position.toInt(), count)
            position += count
            onRead(count)
            return count
        }

        protected open fun onRead(count: Int) = Unit

        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException("read-only")
        override fun position(): Long = position
        override fun position(newPosition: Long): SeekableByteChannel = apply { position = newPosition }
        override fun size(): Long = data.size.toLong()
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException("read-only")
        override fun isOpen(): Boolean = open
        override fun close() { open = false }
    }

    private class CountingChannel(data: ByteArray) : InMemoryChannel(data) {
        var bytesRead = 0L
            private set

        override fun onRead(count: Int) {
            bytesRead += count
        }
    }

    /** Stands in for a SAF document whose provider does not support seeking. */
    private class UnseekableChannel(data: ByteArray) : InMemoryChannel(data) {
        override fun position(newPosition: Long): SeekableByteChannel =
            throw IOException("this document provider does not support seeking")
    }
}
