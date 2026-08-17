package com.brandonmiller.audiobookplayer.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.LibraryBook
import com.brandonmiller.audiobookplayer.ui.BookCover
import com.brandonmiller.audiobookplayer.ui.BookIcon
import com.brandonmiller.audiobookplayer.ui.DocumentIcon
import com.brandonmiller.audiobookplayer.ui.FolderIcon
import com.brandonmiller.audiobookplayer.ui.LibraryCoverSize
import com.brandonmiller.audiobookplayer.ui.PauseIcon
import com.brandonmiller.audiobookplayer.ui.PlayIcon
import com.brandonmiller.audiobookplayer.ui.PlusIcon
import com.brandonmiller.audiobookplayer.ui.ResumeCoverSize
import com.brandonmiller.audiobookplayer.ui.UnavailableBookCover
import com.brandonmiller.audiobookplayer.ui.formatDuration
import com.brandonmiller.audiobookplayer.ui.theme.AudiobookType
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors

/** The screen's gutter. Everything that is not full-bleed sits inside it. */
private val Gutter = 22.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(LocalContext.current)),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val resumeBook by viewModel.resumeBook.collectAsStateWithLifecycle()
    val resumeIsPlaying by viewModel.resumeIsPlaying.collectAsStateWithLifecycle()

    val colors = audiobookColors
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRemoval by remember { mutableStateOf<LibraryBook?>(null) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        // Null when the user dismissed the picker: leave the library alone, say nothing.
        if (treeUri != null) viewModel.addFolder(treeUri)
    }

    val pickFile = rememberLauncherForActivityResult(OpenPersistableDocument()) { documentUri ->
        if (documentUri != null) viewModel.addM4bFile(documentUri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // The resume card already carries this book; listing it again below would be the same book
    // twice on one screen.
    val listedBooks = remember(books, resumeBook) {
        books.filter { it.id != resumeBook?.id }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LibraryHeader(
                enabled = !busy,
                onAddFolder = { pickFolder.launch(null) },
                onAddFile = { pickFile.launch(OpenPersistableDocument.AUDIO_MIME_TYPES) },
            )

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.ink,
                    trackColor = colors.track,
                )
            }

            if (books.isEmpty()) {
                EmptyLibrary(
                    modifier = Modifier.fillMaxSize(),
                    onChooseFolder = { pickFolder.launch(null) },
                    onChooseFile = { pickFile.launch(OpenPersistableDocument.AUDIO_MIME_TYPES) },
                )
            } else {
                resumeBook?.let { book ->
                    ResumeCard(
                        book = book,
                        isPlaying = resumeIsPlaying,
                        onOpen = { onBookClick(book.id.toString()) },
                        onPlayPause = viewModel::toggleResumePlayback,
                        onLongClick = { pendingRemoval = book },
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Gutter),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(listedBooks, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            isUnavailable = book.id in unavailable,
                            onClick = { onBookClick(book.id.toString()) },
                            onLongClick = { pendingRemoval = book },
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { book ->
        RemoveBookSheet(
            book = book,
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                viewModel.remove(book)
                pendingRemoval = null
            },
        )
    }
}

/**
 * The title is content rather than a `TopAppBar`, so it scrolls away with the list instead of
 * holding a bar's worth of height above a screen whose whole point is the books.
 */
@Composable
private fun LibraryHeader(enabled: Boolean, onAddFolder: () -> Unit, onAddFile: () -> Unit) {
    val colors = audiobookColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = AudiobookType.displayScreen,
            color = colors.ink,
        )
        AddMenu(enabled = enabled, onAddFolder = onAddFolder, onAddFile = onAddFile)
    }
}

/**
 * The add control offers both book types rather than assuming one. Dismissing it opens no picker
 * and changes nothing.
 */
@Composable
private fun AddMenu(enabled: Boolean, onAddFolder: () -> Unit, onAddFile: () -> Unit) {
    val colors = audiobookColors
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(CircleShape)
                .background(colors.fill)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            PlusIcon(
                size = 22.dp,
                color = colors.ink,
                contentDescription = stringResource(R.string.library_add),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_add_folder)) },
                onClick = {
                    expanded = false
                    onAddFolder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_add_file)) },
                onClick = {
                    expanded = false
                    onAddFile()
                },
            )
        }
    }
}

/**
 * The book in progress, offered on its own terms: one tap to keep listening, without the Player
 * having to open first.
 *
 * The remaining time and the progress bar appear only when the book's total length is known, which
 * for a folder book means after its first open (design D3). The card is still fully usable without
 * them — resuming does not depend on knowing how much is left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResumeCard(
    book: LibraryBook,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = audiobookColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press removes, exactly as a list row does. Without it, a library holding one
            // book has no way to remove that book at all: it is the book in progress, so it is on
            // this card and therefore not in the list below, where the only remove gesture lives.
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(horizontal = Gutter)
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookCover(artworkPath = book.artworkPath, size = ResumeCoverSize, tintSeed = book.id)

        // A minimum rather than a fixed height. At the cover's height exactly, a title that wraps to
        // two lines leaves the play control less than its 62dp and the Column squeezes it into a
        // pill — the one element on this card that has to stay a circle. Letting the card grow
        // instead costs a few dp of list and keeps the control the size it is meant to be hit at.
        Column(
            modifier = Modifier.weight(1f).heightIn(min = ResumeCoverSize),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.library_resume_label),
                    style = AudiobookType.labelCaps,
                    color = colors.textQuaternary,
                )
                Text(
                    text = book.title,
                    style = AudiobookType.titleResume,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    book.remainingMs?.let { remaining ->
                        Text(
                            text = stringResource(R.string.library_resume_remaining, formatDuration(remaining)),
                            style = AudiobookType.monoMeta,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // The resume card is a library row too, and it is the book most likely to have
                    // an ebook linked — leaving it unmarked here would be the conspicuous gap.
                    if (book.hasEbook) {
                        BookIcon(
                            size = 14.dp,
                            color = colors.textSecondary,
                            filled = true,
                            contentDescription = stringResource(R.string.ebook_linked_indicator),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(colors.ink)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        PauseIcon(26.dp, colors.onInk, stringResource(R.string.player_pause))
                    } else {
                        PlayIcon(26.dp, colors.onInk, stringResource(R.string.player_play))
                    }
                }

                book.progress?.let { fraction ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.track),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(colors.ink),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: LibraryBook,
    isUnavailable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = audiobookColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isUnavailable) {
            UnavailableBookCover(size = LibraryCoverSize)
        } else {
            BookCover(artworkPath = book.artworkPath, size = LibraryCoverSize, tintSeed = book.id)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = book.title,
                style = AudiobookType.titleRow,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (isUnavailable) {
                Text(
                    text = stringResource(R.string.library_unavailable),
                    style = AudiobookType.labelUnavailable,
                    color = colors.error,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // The duration is omitted rather than faked when it is not yet known — see
                        // `LibraryBook.durationMs`.
                        text = book.durationMs?.let {
                            stringResource(R.string.library_row_meta, book.chapterCount, formatDuration(it))
                        } ?: stringResource(R.string.library_row_meta_no_duration, book.chapterCount),
                        style = AudiobookType.monoMeta,
                        color = colors.textTertiary,
                    )
                    // An indicator, not a control: the row stays one large tap target, and the
                    // reader is one further tap away regardless (`add-ebook-companion` design D16).
                    if (book.hasEbook) {
                        Spacer(Modifier.width(8.dp))
                        BookIcon(
                            size = 14.dp,
                            color = colors.textTertiary,
                            filled = true,
                            contentDescription = stringResource(R.string.ebook_linked_indicator),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The empty library is the add flow, rather than a message about it. Both buttons open their
 * picker directly, so getting the first book in is one tap rather than a menu and then a tap.
 */
@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onChooseFolder: () -> Unit,
    onChooseFile: () -> Unit,
) {
    val colors = audiobookColors

    Column(
        modifier = modifier.padding(horizontal = 30.dp).padding(bottom = 60.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = AudiobookType.displayEmpty,
            color = colors.ink,
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.library_empty_body),
            style = AudiobookType.bodyEmpty,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(26.dp))

        EmptyStateButton(
            label = stringResource(R.string.library_empty_folder),
            background = colors.ink,
            content = colors.onInk,
            onClick = onChooseFolder,
        ) { size, color -> FolderIcon(size, color, contentDescription = null) }

        Spacer(Modifier.height(12.dp))

        EmptyStateButton(
            label = stringResource(R.string.library_empty_file),
            background = colors.fill,
            content = colors.ink,
            onClick = onChooseFile,
        ) { size, color -> DocumentIcon(size, color, contentDescription = null) }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.library_empty_privacy),
            style = AudiobookType.monoMicro,
            color = colors.textQuaternary,
        )
    }
}

@Composable
private fun EmptyStateButton(
    label: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    icon: @Composable (Dp, Color) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon(24.dp, content)
        Text(text = label, style = AudiobookType.bodyLargeStrong, color = content)
    }
}

/**
 * A sheet rather than a centered dialog, so the destructive choice and the way out of it are both
 * where a thumb already is. Dismissing it — swipe, scrim tap, or back — removes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveBookSheet(book: LibraryBook, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                text = stringResource(R.string.library_remove_title, book.title),
                style = AudiobookType.titleResume,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.library_remove_body),
                style = AudiobookType.bodyDialog,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(12.dp))

            // Destructive first, so the button nearest the thumb is the one that keeps the book.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    label = stringResource(R.string.library_remove_confirm),
                    background = colors.errorFill,
                    content = colors.onErrorFill,
                    onClick = onConfirm,
                )
                SheetButton(
                    label = stringResource(R.string.library_remove_cancel),
                    background = colors.fillOnRaised,
                    content = colors.ink,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
) {
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

/** What the add control fades to while a scan is running. Matches the design's 0.4. */
private const val DISABLED_ALPHA = 0.4f
