package com.brandonmiller.audiobookplayer.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ui.ChevronIcon
import com.brandonmiller.audiobookplayer.ui.HorizontalDirection
import com.brandonmiller.audiobookplayer.ui.IconTooltip
import com.brandonmiller.audiobookplayer.ui.OverflowIcon
import com.brandonmiller.audiobookplayer.ui.formatTime
import com.brandonmiller.audiobookplayer.ui.theme.AudiobookType
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors
import java.util.Locale

/**
 * One book's marks and notes.
 *
 * A screen rather than a sheet (`add-notes-and-bookmarks` design D7): this is where note text gets
 * typed, and a soft keyboard under a partially expanded sheet would cover the thing being edited.
 *
 * Tapping a row plays the book from that note and comes straight back to the Player, which is what
 * the list is for. Everything else a note can have done to it lives behind the row's own menu, so
 * that the large, obvious target is the one used most.
 */
@Composable
fun NotesScreen(
    bookId: String,
    openNoteId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = viewModel(
        factory = NotesViewModel.factory(LocalContext.current, bookId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = audiobookColors

    var editing by remember { mutableStateOf<NoteRow?>(null) }
    var deleting by remember { mutableStateOf<NoteRow?>(null) }

    /**
     * Whether the open editor belongs to an entry that was created by this visit rather than one
     * that already existed. It decides what abandoning the editor means: discarding something the
     * user never confirmed, or leaving an existing entry alone.
     */
    var editingIsNew by remember { mutableStateOf(false) }

    // Marking sends the user here with a note named, so marking and writing up read as one gesture
    // rather than two visits (design D8).
    //
    // The id is consumed once and then dropped. It has to be watched alongside the list, because the
    // row it names does not exist until the first emission arrives — but keeping text writes to that
    // same list, so an effect that stayed armed would re-fire on its own save and reopen the editor
    // the instant it was closed.
    var pendingOpenId by remember(openNoteId) { mutableStateOf(openNoteId) }
    LaunchedEffect(pendingOpenId, state.notes) {
        val id = pendingOpenId ?: return@LaunchedEffect
        state.notes.firstOrNull { it.id == id }?.let {
            editing = it
            editingIsNew = true
            pendingOpenId = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.surface,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NotesHeader(bookTitle = state.bookTitle, onBack = onBack)

            if (state.loaded && state.notes.isEmpty()) {
                NotesEmpty()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteRowItem(
                            note = note,
                            onSelect = {
                                viewModel.seekTo(note.id)
                                onBack()
                            },
                            onEdit = {
                                editing = note
                                editingIsNew = false
                            },
                            onDelete = { deleting = note },
                        )
                    }
                }
            }
        }
    }

    editing?.let { note ->
        NoteEditor(
            note = note,
            // Abandoning the editor for an entry this visit created discards it, so canceling a
            // mark leaves nothing behind — the entry was never confirmed. Abandoning the editor for
            // an entry that already existed only declines the edit, and keeps it.
            //
            // A bookmark is still reachable: keeping an empty field stores a note with no text,
            // which is what a bare mark is.
            onDismiss = {
                if (editingIsNew) viewModel.delete(note.id)
                editing = null
                editingIsNew = false
            },
            onSave = { text ->
                viewModel.setText(note.id, text)
                editing = null
                editingIsNew = false
            },
        )
    }

    deleting?.let { note ->
        DeleteNoteSheet(
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(note.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun NotesHeader(bookTitle: String, onBack: () -> Unit) {
    val colors = audiobookColors

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconTooltip(stringResource(R.string.notes_back)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    ChevronIcon(
                        direction = HorizontalDirection.Left,
                        size = 24.dp,
                        color = colors.ink,
                        contentDescription = stringResource(R.string.notes_back),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.notes_title),
                    style = AudiobookType.titleSheet,
                    color = colors.ink,
                )
                if (bookTitle.isNotBlank()) {
                    Text(
                        text = bookTitle.uppercase(Locale.getDefault()),
                        style = AudiobookType.monoMeta,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Hairline()
    }
}

/**
 * The chapter and the timestamp lead, because that pair is what makes a note findable months later —
 * the chapter as recorded when the note was taken, the position derived now (design D4, D12).
 *
 * A note with no text yet says so rather than leaving the row looking broken. Both forms are the
 * same record at different stages, so they get the same row rather than two different treatments.
 */
@Composable
private fun NoteRowItem(
    note: NoteRow,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = audiobookColors
    var menuOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(start = 22.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.player_chapter_line_note,
                        note.chapterTitle,
                        formatTime(note.absolutePositionMs),
                    ).uppercase(Locale.getDefault()),
                    style = AudiobookType.monoCaps,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Selectable so a note can be copied out to wherever the book club discussion
                // happens. Nothing else in the app is, because nothing else is the user's own words.
                SelectionContainer {
                    Text(
                        text = note.text ?: stringResource(R.string.note_untitled),
                        style = AudiobookType.bodyLarge,
                        color = if (note.text != null) colors.ink else colors.textQuaternary,
                    )
                }
            }

            Box {
                IconTooltip(stringResource(R.string.note_actions)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        OverflowIcon(
                            size = 20.dp,
                            color = colors.textTertiary,
                            contentDescription = stringResource(R.string.note_actions),
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.surfaceRaised,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_edit), color = colors.ink) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_delete), color = colors.ink) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Hairline()
    }
}

@Composable
private fun NotesEmpty() {
    val colors = audiobookColors

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.notes_empty_title),
            style = AudiobookType.titleResume,
            color = colors.ink,
        )
        Text(
            text = stringResource(R.string.notes_empty_body),
            style = AudiobookType.bodyEmpty,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Where note text is typed — an ordinary text field, which is the whole of this feature's dictation
 * story (design D3). The keyboard's own mic key does the work, so the app needs no speech code and
 * no microphone permission.
 *
 * Dismissing keeps the previous text: an edit is abandoned by leaving it, and only `Keep` writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(note: NoteRow, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = audiobookColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(note.id) { mutableStateOf(note.text.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    // Straight into typing. Reaching this sheet is always a deliberate act — a menu item, or the
    // snackbar's own offer — so there is nothing to read first.
    LaunchedEffect(note.id) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null,
        scrimColor = colors.scrim,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.player_chapter_line_note,
                    note.chapterTitle,
                    formatTime(note.absolutePositionMs),
                ).uppercase(Locale.getDefault()),
                style = AudiobookType.monoCaps,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = {
                    Text(stringResource(R.string.note_edit_hint), color = colors.textQuaternary)
                },
                textStyle = AudiobookType.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                ),
                minLines = 3,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    label = stringResource(R.string.note_edit_save),
                    background = colors.ink,
                    content = colors.onInk,
                    onClick = { onSave(draft) },
                )
                SheetButton(
                    label = stringResource(R.string.note_edit_cancel),
                    background = colors.fillOnRaised,
                    content = colors.ink,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** A sheet rather than a dialog, matching how removing a book is confirmed on the Library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteNoteSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = audiobookColors
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null,
        scrimColor = colors.scrim,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.note_delete_title),
                style = AudiobookType.titleResume,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.note_delete_body),
                style = AudiobookType.bodyDialog,
                color = colors.textSecondary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Destructive first, so the button nearest the thumb is the one that keeps the note.
                SheetButton(
                    label = stringResource(R.string.note_delete_confirm),
                    background = colors.errorFill,
                    content = colors.onErrorFill,
                    onClick = onConfirm,
                )
                SheetButton(
                    label = stringResource(R.string.note_delete_cancel),
                    background = colors.fillOnRaised,
                    content = colors.ink,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, background: Color, content: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AudiobookType.bodyLargeStrong, color = content)
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(audiobookColors.track))
}
