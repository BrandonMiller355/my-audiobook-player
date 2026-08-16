package com.brandonmiller.audiobookplayer.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The containment guard that decides whether removal is allowed to delete a file.
 *
 * This is the check standing between "reclaim the space the app spent on its own sample" and
 * "delete a book off the user's disk", which is the single thing the PRD forbids most plainly
 * (design D9). It is tested against real directories rather than string fixtures because the
 * cases that would break it — `..`, a name that merely shares a prefix — are filesystem
 * behaviors, not string behaviors.
 */
class SampleLibraryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var sampleDirectory: File

    private fun sampleDir(): File =
        if (::sampleDirectory.isInitialized) sampleDirectory
        else temporaryFolder.newFolder("files", "sample").also { sampleDirectory = it }

    @Test
    fun `a file directly inside the directory is contained`() {
        val directory = sampleDir()
        val book = File(directory, "The Mystery of Black Rock Creek.m4b").apply { writeText("x") }

        assertTrue(isContainedIn(directory, book))
    }

    @Test
    fun `a file in a subdirectory is contained`() {
        val directory = sampleDir()
        val nested = File(directory, "nested").apply { mkdirs() }

        assertTrue(isContainedIn(directory, File(nested, "book.m4b")))
    }

    @Test
    fun `the directory itself is not contained in itself`() {
        val directory = sampleDir()

        // Nothing should ever delete the directory by handing it in as a candidate file.
        assertFalse(isContainedIn(directory, directory))
    }

    @Test
    fun `a sibling whose name merely starts the same is not contained`() {
        val directory = sampleDir()
        val sibling = File(directory.parentFile, "sample-backup").apply { mkdirs() }
        val book = File(sibling, "book.m4b")

        // Without the trailing separator in the comparison this passes, and removal starts
        // deleting out of a directory it never wrote to.
        assertFalse(isContainedIn(directory, book))
    }

    @Test
    fun `a path climbing out with dot dot is not contained`() {
        val directory = sampleDir()
        val outside = File(directory, "../../elsewhere/book.m4b")

        assertFalse(isContainedIn(directory, outside))
    }

    @Test
    fun `an unrelated absolute path is not contained`() {
        val directory = sampleDir()
        val elsewhere = temporaryFolder.newFile("users-own-book.m4b")

        assertFalse(isContainedIn(directory, elsewhere))
    }
}
