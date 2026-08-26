package com.brandonmiller.audiobookplayer.ui.notes

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.AudiobookEntity
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.LibraryDao
import com.brandonmiller.audiobookplayer.data.SpeedPreferences
import com.brandonmiller.audiobookplayer.playback.PlaybackService
import com.brandonmiller.audiobookplayer.playback.PlayerTarget
import com.brandonmiller.audiobookplayer.playback.loadBook
import com.brandonmiller.audiobookplayer.playback.mediaIdBelongsTo
import com.brandonmiller.audiobookplayer.playback.storedChapterTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row of the notes list.
 *
 * [absolutePositionMs] is derived here rather than stored on the note (design D12). A stored figure
 * would freeze against chapter durations that legitimately improve — a folder book resolves its
 * lengths lazily, so a note taken early in a session would carry a total that was never right.
 * [chapterTitle] is the opposite case and *is* stored, because a rescan can renumber the chapters
 * underneath it.
 */
data class NoteRow(
    val id: Long,
    val chapterTitle: String,
    val absolutePositionMs: Long,
    val text: String?,
)

data class NotesUiState(
    val bookTitle: String = "",
    val notes: List<NoteRow> = emptyList(),
    /** False until the book has been read, so the empty state is not shown before the list arrives. */
    val loaded: Boolean = false,
)

class NotesViewModel(
    private val appContext: Context,
    private val dao: LibraryDao,
    private val speedPreferences: SpeedPreferences,
    private val bookId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    /**
     * Each note's raw anchor, by note id. Kept here rather than on [NoteRow] so that player
     * coordinates stay out of the UI model — the screen shows a chapter and a timestamp, and has no
     * business knowing which media item a note lives in.
     */
    private var anchors: Map<Long, PlayerTarget> = emptyMap()

    /**
     * Everything [seekTo] could otherwise have had to fetch, read once when this screen opens.
     *
     * Prefetched because seeking has to be **synchronous**: selecting a note seeks and immediately
     * pops back to the Player, which clears this ViewModel and would cancel any coroutine still in
     * flight. A seek that loses a race with its own navigation is a seek that silently does nothing.
     */
    private var book: AudiobookEntity? = null
    private var chapters: List<ChapterEntity> = emptyList()
    private var fallbackSpeed: Float = 1.0f

    init {
        connect()
        observeNotes()
    }

    /**
     * Rebuilt on every emission rather than held: the timeline is cheap to construct from rows
     * already in memory, and a folder book's ends arrive over time, so a cached one would keep
     * reporting the figures that were available the first time this screen opened.
     */
    private fun observeNotes() {
        viewModelScope.launch {
            book = withContext(Dispatchers.IO) { dao.findBook(bookId) }
            chapters = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }
            fallbackSpeed = speedPreferences.lastUsedSpeed()
            _state.update { it.copy(bookTitle = book?.title.orEmpty()) }

            val timeline = storedChapterTimeline(book?.sourceType.orEmpty(), chapters)

            dao.observeNotes(bookId).collect { notes ->
                anchors = notes.associate { it.id to PlayerTarget(it.mediaItemIndex, it.positionMs) }
                val rows = notes.map { note ->
                    NoteRow(
                        id = note.id,
                        chapterTitle = note.chapterTitle,
                        absolutePositionMs = timeline.absolutePosition(
                            timeline.locate(note.mediaItemIndex, note.positionMs),
                        ),
                        text = note.text,
                    )
                }
                _state.update { it.copy(notes = rows, loaded = true) }
            }
        }
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /**
     * Moves playback to a note's anchor, and nothing else.
     *
     * The anchor is stored in raw player coordinates (design D2), so this is a plain `seekTo` with
     * no translation for either book shape — the reason those two columns are what a note stores.
     *
     * Whether the audio is playing is deliberately untouched: a paused book stays paused at the new
     * position and a playing one keeps playing, which is what reviewing notes at a desk and picking
     * one up mid-listen respectively need.
     *
     * The session usually already holds this book, because the Player for it is directly beneath
     * this screen on the back stack. The fallback covers the case where it does not — the user
     * started another book from the library's resume card, say — by loading this one first, through
     * the same [loadBook] both other screens use rather than a third copy of that sequence.
     *
     * Every call here is synchronous, and deliberately: the caller navigates away in the same
     * gesture, so anything deferred to a coroutine would be canceled with this ViewModel. See the
     * prefetched fields above.
     */
    fun seekTo(noteId: Long) {
        val player = controller ?: return
        val anchor = anchors[noteId] ?: return

        if (!mediaIdBelongsTo(player.currentMediaItem?.mediaId, bookId)) {
            val loaded = book ?: return
            player.loadBook(loaded, chapters, fallbackSpeed)
        }

        player.seekTo(anchor.mediaItemIndex, anchor.positionMs)
    }

    /** Blank text stores null, which is a bare mark again rather than a deleted note (design D1). */
    fun setText(noteId: Long, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNoteText(noteId, text.trim().ifBlank { null })
        }
    }

    fun delete(noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteNote(noteId) }
    }

    override fun onCleared() {
        // Releases this connection only. The service keeps playing, which is the point.
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        fun factory(context: Context, bookId: String): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    NotesViewModel(
                        appContext = appContext,
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        speedPreferences = SpeedPreferences(appContext),
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }
}
