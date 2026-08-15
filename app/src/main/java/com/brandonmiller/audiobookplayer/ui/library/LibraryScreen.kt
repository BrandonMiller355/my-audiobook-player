package com.brandonmiller.audiobookplayer.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.LibraryBook
import com.brandonmiller.audiobookplayer.ui.BookCover
import com.brandonmiller.audiobookplayer.ui.LibraryCoverSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    AddMenu(
                        enabled = !busy,
                        onAddFolder = { pickFolder.launch(null) },
                        onAddFile = { pickFile.launch(OpenPersistableDocument.AUDIO_MIME_TYPES) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (books.isEmpty()) {
                EmptyLibrary(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            isUnavailable = book.id in unavailable,
                            onClick = { onBookClick(book.id.toString()) },
                            onLongClick = { pendingRemoval = book },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    pendingRemoval?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = { Text(stringResource(R.string.library_remove_body, book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(book)
                    pendingRemoval = null
                }) { Text(stringResource(R.string.library_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.library_remove_cancel))
                }
            },
        )
    }
}

/**
 * The add control offers both book types rather than assuming one. Dismissing it opens no picker
 * and changes nothing.
 */
@Composable
private fun AddMenu(enabled: Boolean, onAddFolder: () -> Unit, onAddFile: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.library_add))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: LibraryBook,
    isUnavailable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(artworkPath = book.artworkPath, size = LibraryCoverSize)

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isUnavailable) {
                    stringResource(R.string.library_unavailable)
                } else {
                    pluralStringResource(R.plurals.library_chapter_count, book.chapterCount, book.chapterCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUnavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
