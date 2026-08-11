package com.brandonmiller.audiobookplayer.m4b

import java.nio.channels.SeekableByteChannel

/**
 * Reads embedded chapter marks out of an `.m4b` (MP4) file.
 *
 * Media3 does not expose MP4 chapter atoms, so this is the one place the project parses a
 * binary container itself. Two encodings exist in the wild and both are supported: the Nero
 * `chpl` atom under `moov/udta`, and the QuickTime chapter track reached through a `tref/chap`
 * reference.
 *
 * Reads metadata only — never the `mdat` audio payload, and never the audio track's own sample
 * tables, which run to megabytes in real files.
 */
object M4bChapterParser {

    /**
     * A lone mark covering the whole file conveys nothing to navigate by, so it counts as
     * unchaptered. Most encoders put it at exactly zero; this allows a little slack for those
     * that do not.
     */
    private const val WHOLE_FILE_START_TOLERANCE_MS = 1_000L

    private const val MAX_CHAPTERS = 10_000
    private const val MAX_TITLE_BYTES = 4_096

    /** Nero timestamps are in 100-nanosecond units. */
    private const val NERO_UNITS_PER_MS = 10_000L

    /**
     * Never throws. Malformed metadata must not be able to crash the app (PRD §22), so every
     * failure — including a byte source that cannot seek — comes back as [ChapterParseResult.Unreadable].
     */
    fun parse(channel: SeekableByteChannel): ChapterParseResult =
        try {
            parseChapters(Mp4Reader(channel))
        } catch (e: Mp4FormatException) {
            ChapterParseResult.Unreadable(e.message ?: "malformed MP4 data")
        } catch (e: Exception) {
            // Broad on purpose: a non-seekable channel, a closed channel, and an arithmetic edge
            // in hostile data all surface differently, and none of them may reach the caller.
            ChapterParseResult.Unreadable("could not read the file (${e.javaClass.simpleName}: ${e.message})")
        }

    private fun parseChapters(reader: Mp4Reader): ChapterParseResult {
        val moov = reader.children(0, reader.size).firstOfType("moov")
            ?: return ChapterParseResult.Unreadable("no moov box found; not an MP4 container")
        val moovChildren = reader.children(moov.payloadStart, moov.end)

        val durationMs = movieDurationMs(reader, moovChildren)
            ?: return ChapterParseResult.Unreadable("missing or unusable mvhd box")

        val marks = selectMarks(
            fromChapterTrack = readChapterTrackMarks(reader, moovChildren),
            fromNero = readNeroMarks(reader, moovChildren),
        )
        if (marks.isEmpty()) return ChapterParseResult.Unchaptered

        val chapters = assemble(marks, durationMs)
        return when {
            chapters.isEmpty() -> ChapterParseResult.Unchaptered
            chapters.size == 1 && spansWholeFile(chapters.single(), durationMs) -> ChapterParseResult.Unchaptered
            else -> ChapterParseResult.Chapters(chapters)
        }
    }

    /** A mark as stored in the file: a start time and a title, with no end. */
    private data class Mark(val startMs: Long, val title: String)

    /**
     * The QuickTime track carries an explicit timescale and is what most current encoders write,
     * so it wins when it yields something usable. `chpl` is more often the vestigial copy left
     * behind by a re-mux — but a file whose track is empty and whose `chpl` is good still works.
     */
    private fun selectMarks(fromChapterTrack: List<Mark>, fromNero: List<Mark>): List<Mark> = when {
        fromChapterTrack.size >= 2 -> fromChapterTrack
        fromNero.size >= 2 -> fromNero
        fromChapterTrack.isNotEmpty() -> fromChapterTrack
        else -> fromNero
    }

    // ---------------------------------------------------------------- movie header

    private fun movieDurationMs(reader: Mp4Reader, moovChildren: List<Mp4Box>): Long? {
        val mvhd = moovChildren.firstOfType("mvhd") ?: return null
        if (mvhd.payloadSize < 4) return null

        val version = (reader.u32(mvhd.payloadStart) ushr 24).toInt() and 0xFF
        val timescale: Long
        val duration: Long
        if (version == 1) {
            if (mvhd.payloadSize < 32) return null
            timescale = reader.u32(mvhd.payloadStart + 20)
            duration = reader.u64(mvhd.payloadStart + 24)
        } else {
            if (mvhd.payloadSize < 20) return null
            timescale = reader.u32(mvhd.payloadStart + 12)
            duration = reader.u32(mvhd.payloadStart + 16)
        }
        return toMillis(duration, timescale)
    }

    private fun toMillis(value: Long, timescale: Long): Long? {
        if (timescale <= 0 || value <= 0) return null
        if (value > Long.MAX_VALUE / 1000) return null
        return value * 1000 / timescale
    }

    // ---------------------------------------------------------------- Nero chpl

    private fun readNeroMarks(reader: Mp4Reader, moovChildren: List<Mp4Box>): List<Mark> {
        for (udta in moovChildren.ofType("udta")) {
            val chpl = reader.children(udta.payloadStart, udta.end).firstOfType("chpl") ?: continue
            return readChpl(reader, chpl)
        }
        return emptyList()
    }

    private fun readChpl(reader: Mp4Reader, chpl: Mp4Box): List<Mark> {
        if (chpl.payloadSize < 5) return emptyList()

        var position = chpl.payloadStart
        val version = (reader.u32(position) ushr 24).toInt() and 0xFF
        position += 4

        // Version 1 carries a 32-bit reserved field then a single-byte count; version 0 stores
        // the count as a 32-bit value with no reserved field.
        val declared: Long
        if (version >= 1) {
            if (position + 5 > chpl.end) return emptyList()
            position += 4
            declared = reader.u8(position).toLong()
            position += 1
        } else {
            if (position + 4 > chpl.end) return emptyList()
            declared = reader.u32(position)
            position += 4
        }

        // Smallest possible entry: 8-byte timestamp plus a 1-byte length with no title.
        val limit = boundedCount(declared, ENTRY_BYTES_MIN, chpl.end - position, MAX_CHAPTERS)

        val marks = mutableListOf<Mark>()
        var read = 0
        while (read < limit && position + ENTRY_BYTES_MIN <= chpl.end) {
            val timestamp = reader.u64(position)
            position += 8
            val titleLength = reader.u8(position)
            position += 1
            if (position + titleLength > chpl.end) break
            val title = String(reader.bytes(position, minOf(titleLength, MAX_TITLE_BYTES)), Charsets.UTF_8)
            position += titleLength
            marks += Mark(startMs = timestamp / NERO_UNITS_PER_MS, title = title)
            read++
        }
        return marks
    }

    private const val ENTRY_BYTES_MIN = 9

    // ---------------------------------------------------------------- QuickTime chapter track

    private fun readChapterTrackMarks(reader: Mp4Reader, moovChildren: List<Mp4Box>): List<Mark> {
        val traks = moovChildren.ofType("trak")
        val referenced = referencedChapterTrackIds(reader, traks)
        if (referenced.isEmpty()) return emptyList()

        for (trak in traks) {
            val trakChildren = reader.children(trak.payloadStart, trak.end)
            val trackId = trackId(reader, trakChildren) ?: continue
            if (trackId !in referenced) continue

            val mdia = trakChildren.firstOfType("mdia") ?: continue
            val mdiaChildren = reader.children(mdia.payloadStart, mdia.end)

            // Real files reference a 'vide' track here too, for per-chapter thumbnails. Only the
            // 'text' track carries the titles.
            if (handlerType(reader, mdiaChildren) != "text") continue
            val timescale = mediaTimescale(reader, mdiaChildren) ?: continue

            return readTextSamples(reader, mdiaChildren, timescale)
        }
        return emptyList()
    }

    private fun referencedChapterTrackIds(reader: Mp4Reader, traks: List<Mp4Box>): Set<Long> {
        val ids = mutableSetOf<Long>()
        for (trak in traks) {
            val tref = reader.children(trak.payloadStart, trak.end).firstOfType("tref") ?: continue
            for (chap in reader.children(tref.payloadStart, tref.end).ofType("chap")) {
                var position = chap.payloadStart
                while (position + 4 <= chap.end) {
                    ids += reader.u32(position)
                    position += 4
                }
            }
        }
        return ids
    }

    private fun trackId(reader: Mp4Reader, trakChildren: List<Mp4Box>): Long? {
        val tkhd = trakChildren.firstOfType("tkhd") ?: return null
        if (tkhd.payloadSize < 4) return null
        val version = (reader.u32(tkhd.payloadStart) ushr 24).toInt() and 0xFF
        val offset = if (version == 1) 20L else 12L
        if (tkhd.payloadSize < offset + 4) return null
        return reader.u32(tkhd.payloadStart + offset)
    }

    private fun handlerType(reader: Mp4Reader, mdiaChildren: List<Mp4Box>): String? {
        val hdlr = mdiaChildren.firstOfType("hdlr") ?: return null
        if (hdlr.payloadSize < 12) return null
        return reader.type(hdlr.payloadStart + 8)
    }

    private fun mediaTimescale(reader: Mp4Reader, mdiaChildren: List<Mp4Box>): Long? {
        val mdhd = mdiaChildren.firstOfType("mdhd") ?: return null
        if (mdhd.payloadSize < 4) return null
        val version = (reader.u32(mdhd.payloadStart) ushr 24).toInt() and 0xFF
        val offset = if (version == 1) 20L else 12L
        if (mdhd.payloadSize < offset + 4) return null
        return reader.u32(mdhd.payloadStart + offset).takeIf { it > 0 }
    }

    private fun readTextSamples(reader: Mp4Reader, mdiaChildren: List<Mp4Box>, timescale: Long): List<Mark> {
        val minf = mdiaChildren.firstOfType("minf") ?: return emptyList()
        val stbl = reader.children(minf.payloadStart, minf.end).firstOfType("stbl") ?: return emptyList()
        val stblChildren = reader.children(stbl.payloadStart, stbl.end)

        val startTimes = readSampleStartTimes(reader, stblChildren.firstOfType("stts") ?: return emptyList())
        if (startTimes.isEmpty()) return emptyList()

        val sizes = readSampleSizes(reader, stblChildren.firstOfType("stsz") ?: return emptyList())
        if (sizes.isEmpty()) return emptyList()

        val offsets = readSampleOffsets(reader, stblChildren, sizes)
        if (offsets.isEmpty()) return emptyList()

        val count = minOf(startTimes.size, sizes.size, offsets.size)
        val marks = mutableListOf<Mark>()
        for (i in 0 until count) {
            val startMs = toMillis(startTimes[i], timescale) ?: if (startTimes[i] == 0L) 0L else continue
            marks += Mark(startMs = startMs, title = readSampleTitle(reader, offsets[i], sizes[i]))
        }
        return marks
    }

    /** `stts` stores run-length encoded durations; expanding them gives each sample's start. */
    private fun readSampleStartTimes(reader: Mp4Reader, stts: Mp4Box): List<Long> {
        if (stts.payloadSize < 8) return emptyList()
        var position = stts.payloadStart + 4
        val declared = reader.u32(position)
        position += 4
        val entries = boundedCount(declared, 8, stts.end - position, MAX_CHAPTERS)

        val startTimes = mutableListOf<Long>()
        var running = 0L
        for (entry in 0 until entries) {
            if (position + 8 > stts.end) break
            val sampleCount = reader.u32(position)
            val delta = reader.u32(position + 4)
            position += 8
            var produced = 0L
            while (produced < sampleCount && startTimes.size < MAX_CHAPTERS) {
                startTimes += running
                running += delta
                produced++
            }
            if (startTimes.size >= MAX_CHAPTERS) break
        }
        return startTimes
    }

    private fun readSampleSizes(reader: Mp4Reader, stsz: Mp4Box): List<Int> {
        if (stsz.payloadSize < 12) return emptyList()
        val uniformSize = reader.u32(stsz.payloadStart + 4)
        val declaredCount = reader.u32(stsz.payloadStart + 8)

        if (uniformSize > 0) {
            val count = minOf(declaredCount, MAX_CHAPTERS.toLong()).toInt()
            return List(count) { uniformSize.toInt() }
        }

        var position = stsz.payloadStart + 12
        val count = boundedCount(declaredCount, 4, stsz.end - position, MAX_CHAPTERS)
        val sizes = ArrayList<Int>(count)
        repeat(count) {
            sizes += reader.u32(position).toInt()
            position += 4
        }
        return sizes
    }

    /** Sample index to file offset, resolved through `stsc` and `stco`/`co64`. */
    private fun readSampleOffsets(reader: Mp4Reader, stblChildren: List<Mp4Box>, sizes: List<Int>): List<Long> {
        val chunkOffsets = readChunkOffsets(reader, stblChildren)
        if (chunkOffsets.isEmpty()) return emptyList()

        val stsc = stblChildren.firstOfType("stsc") ?: return emptyList()
        if (stsc.payloadSize < 8) return emptyList()
        var position = stsc.payloadStart + 4
        val declared = reader.u32(position)
        position += 4
        val entries = boundedCount(declared, 12, stsc.end - position, MAX_CHAPTERS)
        if (entries == 0) return emptyList()

        val firstChunks = LongArray(entries)
        val samplesPerChunk = LongArray(entries)
        for (i in 0 until entries) {
            firstChunks[i] = reader.u32(position)
            samplesPerChunk[i] = reader.u32(position + 4)
            position += 12
        }

        val offsets = mutableListOf<Long>()
        for (entry in 0 until entries) {
            val firstChunk = firstChunks[entry]
            val perChunk = samplesPerChunk[entry]
            val lastChunk = if (entry + 1 < entries) firstChunks[entry + 1] - 1 else chunkOffsets.size.toLong()
            var chunk = firstChunk
            while (chunk <= lastChunk && offsets.size < sizes.size) {
                val chunkIndex = (chunk - 1).toInt()
                if (chunkIndex < 0 || chunkIndex >= chunkOffsets.size) break
                var offset = chunkOffsets[chunkIndex]
                var inChunk = 0L
                while (inChunk < perChunk && offsets.size < sizes.size) {
                    offsets += offset
                    offset += sizes[offsets.size - 1]
                    inChunk++
                }
                chunk++
            }
            if (offsets.size >= sizes.size) break
        }
        return offsets
    }

    private fun readChunkOffsets(reader: Mp4Reader, stblChildren: List<Mp4Box>): List<Long> {
        stblChildren.firstOfType("stco")?.let { return readOffsetTable(reader, it, entryBytes = 4) }
        stblChildren.firstOfType("co64")?.let { return readOffsetTable(reader, it, entryBytes = 8) }
        return emptyList()
    }

    private fun readOffsetTable(reader: Mp4Reader, box: Mp4Box, entryBytes: Int): List<Long> {
        if (box.payloadSize < 8) return emptyList()
        var position = box.payloadStart + 4
        val declared = reader.u32(position)
        position += 4
        val count = boundedCount(declared, entryBytes, box.end - position, MAX_CHAPTERS)
        val offsets = ArrayList<Long>(count)
        repeat(count) {
            offsets += if (entryBytes == 8) reader.u64(position) else reader.u32(position)
            position += entryBytes
        }
        return offsets
    }

    /**
     * A QuickTime text sample is a 2-byte big-endian length followed by that many bytes, and may
     * be followed by encoding atoms which carry no title text and are ignored.
     */
    private fun readSampleTitle(reader: Mp4Reader, offset: Long, sampleSize: Int): String {
        if (sampleSize < 2 || offset + 2 > reader.size) return ""
        val declared = reader.u16(offset)
        val available = minOf(declared, sampleSize - 2, MAX_TITLE_BYTES)
        if (available <= 0) return ""
        if (offset + 2 + available > reader.size) return ""
        return decodeTitle(reader.bytes(offset + 2, available))
    }

    private fun decodeTitle(bytes: ByteArray): String {
        val hasBom = bytes.size >= 2 &&
            (
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
                    (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
                )
        // Undecodable bytes become replacement characters rather than an error — a mangled title
        // is better than a failed book.
        return String(bytes, if (hasBom) Charsets.UTF_16 else Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- assembly

    /**
     * Neither encoding stores chapter ends, so each is the next chapter's start and the last is
     * the movie duration. Marks that cannot yield a positive duration are dropped.
     */
    private fun assemble(marks: List<Mark>, durationMs: Long): List<Chapter> {
        val ordered = marks
            .filter { it.startMs >= 0 && it.startMs < durationMs }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }

        val chapters = mutableListOf<Chapter>()
        for (i in ordered.indices) {
            val start = ordered[i].startMs
            val end = if (i + 1 < ordered.size) ordered[i + 1].startMs else durationMs
            if (end <= start) continue
            chapters += Chapter(index = chapters.size, title = ordered[i].title, startMs = start, endMs = end)
        }
        return chapters
    }

    private fun spansWholeFile(chapter: Chapter, durationMs: Long): Boolean =
        chapter.startMs <= WHOLE_FILE_START_TOLERANCE_MS && chapter.endMs >= durationMs
}
