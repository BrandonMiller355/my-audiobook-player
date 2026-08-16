package com.brandonmiller.audiobookplayer.ui.library

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Availability for the two kinds of source the library can hold (design D8).
 *
 * The `file://` branch exists because the bundled sample can never hold a persistable SAF grant,
 * and without it the app's own book lists as "Source unavailable" from the moment it is seeded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriPermissionHolderTest {

    private val context = RuntimeEnvironment.getApplication()
    private val permissions = UriPermissionHolder(context)

    @Test
    fun `a file source is available while the file exists`() {
        val file = File(context.filesDir, "sample/book.m4b").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }

        assertTrue(permissions.isHeld(Uri.fromFile(file)))
    }

    @Test
    fun `a file source becomes unavailable once the file is gone`() {
        val file = File(context.filesDir, "sample/missing.m4b")

        // Listed as unavailable and still removable — not a crash, per PRD §22.
        assertFalse(permissions.isHeld(Uri.fromFile(file)))
    }

    @Test
    fun `a document source with no grant is unavailable`() {
        val documentUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABooks")

        assertFalse(permissions.isHeld(documentUri))
    }

    @Test
    fun `releasing a file source does nothing and does not throw`() {
        val file = File(context.filesDir, "sample/book.m4b").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }

        permissions.release(Uri.fromFile(file))

        // There is no grant to give back, and the file is not the release call's business —
        // deleting it belongs to SampleLibrary, guarded by containment.
        assertTrue(file.exists())
    }
}
