package com.brandonmiller.audiobookplayer.m4b

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

internal class Mp4FormatException(message: String) : Exception(message)

/**
 * One box in the MP4 tree. [payloadStart] is the first byte after the header, [end] is one
 * past the box's last byte.
 */
internal data class Mp4Box(val type: String, val payloadStart: Long, val end: Long) {
    val payloadSize: Long get() = end - payloadStart
}

/**
 * Random-access big-endian reader over an MP4 file.
 *
 * Deliberately reads only what it is asked for. `moov` can sit after a multi-gigabyte `mdat`,
 * and chapter title text lives out in `mdat`, so this needs real seeking rather than a stream.
 */
internal class Mp4Reader(private val channel: SeekableByteChannel) {

    val size: Long = channel.size()

    fun read(position: Long, count: Int): ByteBuffer {
        if (count < 0) throw Mp4FormatException("negative read length $count")
        if (position < 0 || position + count > size) {
            throw Mp4FormatException("read of $count bytes at $position runs past end of file ($size)")
        }
        val buffer = ByteBuffer.allocate(count)
        channel.position(position)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) throw Mp4FormatException("unexpected end of file at $position")
        }
        buffer.flip()
        return buffer
    }

    fun u8(position: Long): Int = read(position, 1).get().toInt() and 0xFF

    fun u16(position: Long): Int = read(position, 2).short.toInt() and 0xFFFF

    fun u32(position: Long): Long = read(position, 4).int.toLong() and 0xFFFFFFFFL

    fun u64(position: Long): Long = read(position, 8).long

    fun type(position: Long): String {
        val bytes = ByteArray(4)
        read(position, 4).get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    fun bytes(position: Long, count: Int): ByteArray {
        val out = ByteArray(count)
        read(position, count).get(out)
        return out
    }

    /**
     * Headers of the boxes directly inside [start, end).
     *
     * Stops rather than looping on anything malformed: a box smaller than its own header, a box
     * that would run past its parent, or an implausible number of siblings. Boxes read before
     * the bad one are still returned, so a truncated file yields what was intact.
     */
    fun children(start: Long, end: Long): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var position = start
        var seen = 0
        while (position + HEADER_BYTES <= end) {
            if (++seen > MAX_SIBLINGS) break
            val declared = try {
                u32(position)
            } catch (e: Mp4FormatException) {
                break
            }
            val boxType = try {
                type(position + 4)
            } catch (e: Mp4FormatException) {
                break
            }

            var headerLength = HEADER_BYTES
            var boxSize = declared
            when (declared) {
                1L -> {
                    if (position + EXTENDED_HEADER_BYTES > end) break
                    boxSize = try {
                        u64(position + HEADER_BYTES)
                    } catch (e: Mp4FormatException) {
                        break
                    }
                    headerLength = EXTENDED_HEADER_BYTES
                }
                0L -> boxSize = end - position
            }

            if (boxSize < headerLength) break
            if (position + boxSize > end) break

            boxes += Mp4Box(boxType, position + headerLength, position + boxSize)
            position += boxSize
        }
        return boxes
    }

    private companion object {
        const val HEADER_BYTES = 8L
        const val EXTENDED_HEADER_BYTES = 16L

        /** A well-formed level has a handful of boxes; this only exists to stop pathological input. */
        const val MAX_SIBLINGS = 4096
    }
}

internal fun List<Mp4Box>.firstOfType(type: String): Mp4Box? = firstOrNull { it.type == type }

internal fun List<Mp4Box>.ofType(type: String): List<Mp4Box> = filter { it.type == type }

/**
 * How many fixed-size entries a box can actually hold, given what it declares.
 *
 * Declared counts are never trusted: a corrupt or hostile file can claim four billion entries,
 * and real audio tracks legitimately declare over a million. Both are handled by clamping to
 * the bytes present and to a ceiling.
 */
internal fun boundedCount(declared: Long, entryBytes: Int, availableBytes: Long, ceiling: Int): Int {
    if (declared <= 0) return 0
    val affordable = availableBytes / entryBytes
    return minOf(declared, affordable, ceiling.toLong()).toInt()
}
