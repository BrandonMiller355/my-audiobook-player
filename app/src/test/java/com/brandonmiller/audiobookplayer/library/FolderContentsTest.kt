package com.brandonmiller.audiobookplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderContentsTest {

    private fun file(name: String, mime: String? = "audio/mpeg") =
        ScannedEntry(documentId = "doc:$name", name = name, mimeType = mime)

    private fun directory(name: String) =
        ScannedEntry(documentId = "doc:$name", name = name, mimeType = FolderContents.MIME_TYPE_DIRECTORY)

    @Test
    fun `supported extensions are accepted regardless of case`() {
        assertTrue(FolderContents.isSupportedAudio("a.mp3"))
        assertTrue(FolderContents.isSupportedAudio("a.MP3"))
        assertTrue(FolderContents.isSupportedAudio("a.Mp3"))
        assertTrue(FolderContents.isSupportedAudio("a.m4b"))
        assertTrue(FolderContents.isSupportedAudio("a.flac"))
    }

    @Test
    fun `unsupported and extensionless names are rejected`() {
        assertFalse(FolderContents.isSupportedAudio("a.wma"))
        assertFalse(FolderContents.isSupportedAudio("cover.jpg"))
        assertFalse(FolderContents.isSupportedAudio("README"))
        assertFalse(FolderContents.isSupportedAudio("trailing."))
    }

    @Test
    fun `a generic mime type does not disqualify an audio file`() {
        // Removable storage routinely reports mp3s as application/octet-stream.
        val entries = listOf(file("02.mp3", "application/octet-stream"), file("01.mp3", null))

        assertEquals(listOf("01.mp3", "02.mp3"), FolderContents.audioFilesInOrder(entries).map { it.name })
    }

    @Test
    fun `subdirectories are ignored entirely`() {
        val entries = listOf(
            directory("Metro 2033 - ENGLISH AUDIOBOOK"),
            directory("Metro 2033 - ENGLISH EBOOK"),
            file("readme.txt", "text/plain"),
        )

        assertTrue(FolderContents.audioFilesInOrder(entries).isEmpty())
    }

    @Test
    fun `non-audio files alongside the audio are dropped without affecting the rest`() {
        val entries = listOf(
            file("Born To Run_cover-lg.jpg", "image/jpeg"),
            file("1-02 Introduction.mp3"),
            file("Torrent downloaded from Demonoid.com.txt", "text/plain"),
            file("1-01 RB Intro.mp3"),
            file("Project Hail Mary by Andy Weir.azw3", "application/octet-stream"),
        )

        assertEquals(
            listOf("1-01 RB Intro.mp3", "1-02 Introduction.mp3"),
            FolderContents.audioFilesInOrder(entries).map { it.name },
        )
    }

    @Test
    fun `results come back in natural order`() {
        val entries = listOf(file("Chapter 10.mp3"), file("Chapter 2.mp3"), file("Chapter 1.mp3"))

        assertEquals(
            listOf("Chapter 1.mp3", "Chapter 2.mp3", "Chapter 10.mp3"),
            FolderContents.audioFilesInOrder(entries).map { it.name },
        )
    }

    @Test
    fun `chapter titles drop the extension only`() {
        assertEquals("1-01 Born To Run - RB Intro", FolderContents.chapterTitle("1-01 Born To Run - RB Intro.mp3"))
        assertEquals("no extension", FolderContents.chapterTitle("no extension"))
        assertEquals("dotted.name", FolderContents.chapterTitle("dotted.name.mp3"))
    }
}
