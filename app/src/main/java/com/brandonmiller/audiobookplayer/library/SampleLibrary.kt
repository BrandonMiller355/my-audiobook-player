package com.brandonmiller.audiobookplayer.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

private val Context.sampleDataStore: DataStore<Preferences> by preferencesDataStore(name = "sample_library")

/**
 * The one audiobook the app carries itself, so that a library which has never had a book added to
 * it is not simply empty (`bundle-sample-audiobook`).
 *
 * The asset is copied out of the APK rather than played in place. Playing in place would mean
 * teaching [com.brandonmiller.audiobookplayer.m4b.M4bChapterParser] that position zero of its
 * channel is not the start of the container — an asset is a region of the APK, not a file — and
 * giving `MediaMetadataRetriever` the same offset treatment. A plain copy in `filesDir` is a plain
 * `file://` URI that the parser, the retriever, and ExoPlayer all already handle (design D1).
 *
 * Nothing here touches the user's own files. This class copies the app's asset, and deletes only
 * what it copied.
 */
class SampleLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.sampleDataStore

    /** Everything this class is allowed to create, and the only place it will ever delete from. */
    private val directory = File(appContext.filesDir, DIRECTORY)

    suspend fun alreadySeeded(): Boolean = dataStore.data.first()[SEEDED_KEY] == true

    /**
     * Written only after the book is committed to the library. A crash partway through seeding
     * then retries on the next open; the opposite ordering would trade a rare duplicate for a
     * permanent absence with nothing to notice it by (design D5).
     */
    suspend fun markSeeded() {
        dataStore.edit { it[SEEDED_KEY] = true }
    }

    /**
     * Copies the bundled asset into app-private storage and returns its URI, or null if it could
     * not be written — a full disk being the realistic case, and one that must not take the
     * Library screen down with it.
     *
     * The copy lands under a temporary name and is renamed into place, so an interrupted write
     * cannot leave a truncated `.m4b` behind to be parsed as a broken book on the next attempt.
     */
    fun install(): Uri? = try {
        directory.mkdirs()
        val destination = File(directory, FILE_NAME)
        val partial = File(directory, "$FILE_NAME.part")

        appContext.assets.open(ASSET_PATH).use { source ->
            partial.outputStream().use { source.copyTo(it) }
        }
        check(partial.renameTo(destination)) { "could not rename ${partial.name} into place" }

        Uri.fromFile(destination)
    } catch (e: Exception) {
        Log.w(TAG, "could not install the bundled sample audiobook", e)
        runCatching { File(directory, "$FILE_NAME.part").delete() }
        null
    }

    /**
     * Whether [uri] names a file this class put in app-private storage — the only thing removal is
     * allowed to delete from disk.
     *
     * Containment, not the file's name or the book's source type: those would each be one bug away
     * from deleting a book off the user's own disk, which is the thing the PRD forbids most
     * plainly (design D9). A SAF document URI cannot reach this branch at all, having the `content`
     * scheme rather than `file`.
     */
    fun owns(uri: Uri): Boolean {
        if (uri.scheme != FILE_SCHEME) return false
        val path = uri.path ?: return false
        return isContainedIn(directory, File(path))
    }

    /** Reclaims the copy's space when the user removes the sample. Nothing else is ever deleted. */
    fun delete(uri: Uri) {
        if (!owns(uri)) return
        runCatching { File(uri.path!!).delete() }
    }

    private companion object {
        const val TAG = "SampleLibrary"
        const val DIRECTORY = "sample"
        const val ASSET_PATH = "sample/the-mystery-of-black-rock-creek.m4b"

        /**
         * The book's title is derived from this name by `titleFrom`, exactly as it is for a book
         * the user picks — so the name is spelled the way the title should read rather than the way
         * the asset is spelled (design D4).
         */
        const val FILE_NAME = "The Mystery of Black Rock Creek.m4b"

        const val FILE_SCHEME = "file"
        val SEEDED_KEY = booleanPreferencesKey("sample_seeded")
    }
}

/**
 * Whether [candidate] lies inside [directory], compared on canonical paths so that neither a
 * `..` segment nor a symlink can point outward and still pass.
 *
 * The trailing separator matters: without it, a sibling directory whose name merely starts with
 * this one's — `filesDir/sample-backup` next to `filesDir/sample` — would be judged contained.
 */
internal fun isContainedIn(directory: File, candidate: File): Boolean = runCatching {
    val root = directory.canonicalPath + File.separator
    candidate.canonicalPath.startsWith(root)
}.getOrDefault(false)
