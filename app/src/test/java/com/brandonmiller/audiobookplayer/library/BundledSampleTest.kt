package com.brandonmiller.audiobookplayer.library

import android.net.Uri
import com.brandonmiller.audiobookplayer.m4b.ChapterParseResult
import com.brandonmiller.audiobookplayer.m4b.M4bChapterParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

/**
 * The bundled sample, end to end from the packaged asset: that it is in the APK under the path
 * [SampleLibrary] opens, that it is a real chaptered `.m4b`, and that installing, owning, and
 * deleting it behave.
 *
 * A Robolectric test rather than a plain one because there is no asset and no `filesDir` without a
 * Context — the case `config.yaml` pre-approves it for. The parse here matters beyond this change:
 * every `.m4b` in the owner's own library reports a single book-length chapter, so until this file
 * shipped, `M4bChapterParser`'s chaptered path had only ever run against synthetic fixtures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledSampleTest {

    private lateinit var sample: SampleLibrary
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        sample = SampleLibrary(context)
        filesDir = context.filesDir
    }

    @Test
    fun `the asset installs into app-private storage`() {
        val uri = sample.install()

        assertNotNull("the asset is missing from the APK, or is not at the path SampleLibrary opens", uri)
        val file = File(uri!!.path!!)
        assertTrue(file.exists())
        assertTrue("an empty copy would parse as a broken book", file.length() > 0)
        assertTrue("the copy is inside filesDir", file.canonicalPath.startsWith(filesDir.canonicalPath))
    }

    @Test
    fun `the installed name is the one the title is derived from`() {
        val uri = sample.install()

        // The book's title comes from its file name for every book (design D4), so this name is
        // the title a guest sees in the library.
        assertEquals("The Mystery of Black Rock Creek.m4b", File(uri!!.path!!).name)
        assertEquals("The Mystery of Black Rock Creek", titleFrom(File(uri.path!!).name))
    }

    @Test
    fun `no partial file is left behind by a successful install`() {
        sample.install()

        val leftovers = File(filesDir, "sample").listFiles().orEmpty().filter { it.name.endsWith(".part") }
        assertTrue("a .part file survived a successful copy: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `installing twice overwrites rather than accumulating`() {
        sample.install()
        val second = sample.install()

        val files = File(filesDir, "sample").listFiles().orEmpty()
        assertEquals("expected exactly one file, got ${files.map { it.name }}", 1, files.size)
        assertNotNull(second)
    }

    @Test
    fun `the bundled book carries real chapter marks`() {
        val uri = sample.install()

        val result = FileInputStream(File(uri!!.path!!)).use { M4bChapterParser.parse(it.channel) }

        // Five, per the file's own chapter track. A regression here means the asset was replaced
        // or re-encoded, and the one chaptered book the app ships stopped being chaptered.
        assertTrue("expected chapters, got $result", result is ChapterParseResult.Chapters)
        val chapters = (result as ChapterParseResult.Chapters).chapters
        assertEquals(5, chapters.size)
        assertEquals(0L, chapters.first().startMs)
        assertTrue("chapters must ascend", chapters.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    @Test
    fun `it owns and deletes only its own copy`() {
        val uri = sample.install()!!
        val usersOwnBook = File(filesDir.parentFile, "users-own-book.m4b").apply { writeText("not ours") }

        assertTrue(sample.owns(uri))
        assertFalse("a file outside the sample directory", sample.owns(Uri.fromFile(usersOwnBook)))
        assertFalse("a SAF document URI", sample.owns(Uri.parse("content://com.android.providers/document/1234")))

        sample.delete(Uri.fromFile(usersOwnBook))
        assertTrue("deleting a file it does not own", usersOwnBook.exists())

        sample.delete(uri)
        assertFalse(File(uri.path!!).exists())
    }

    @Test
    fun `seeding is recorded only once it is marked`() = runBlocking {
        assertFalse("a fresh install has not seeded", sample.alreadySeeded())

        sample.install()
        assertFalse("installing the file alone must not count as seeded", sample.alreadySeeded())

        sample.markSeeded()
        assertTrue(sample.alreadySeeded())
        assertTrue("the flag outlives a new instance", SampleLibrary(RuntimeEnvironment.getApplication()).alreadySeeded())
    }
}
