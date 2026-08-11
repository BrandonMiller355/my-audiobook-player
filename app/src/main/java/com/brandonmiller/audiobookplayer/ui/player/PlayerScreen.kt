package com.brandonmiller.audiobookplayer.ui.player

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.ChapterEntity
import com.brandonmiller.audiobookplayer.data.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Still a placeholder — the PRD §20.2 layout arrives with transport controls. It now resolves the
 * route argument to a real book so the plumbing from Library to Player is visible on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(LocalContext.current, bookId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: stringResource(R.string.player_unknown_book)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.title != null) {
                Text(
                    text = pluralStringResource(
                        R.plurals.library_chapter_count,
                        state.chapters.size,
                        state.chapters.size,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                state.chapters.firstOrNull()?.let { first ->
                    Text(
                        text = first.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.player_back))
            }
        }
    }
}

data class PlayerState(
    val title: String? = null,
    val chapters: List<ChapterEntity> = emptyList(),
)

class PlayerViewModel(private val dao: LibraryDao, private val bookId: Long) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { dao.findBook(bookId) }
            val chapters = withContext(Dispatchers.IO) { dao.chaptersFor(bookId) }
            _state.value = PlayerState(title = book?.title, chapters = chapters)
        }
    }

    companion object {
        fun factory(context: Context, bookId: String): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    PlayerViewModel(
                        dao = AudiobookDatabase.get(appContext).libraryDao(),
                        bookId = bookId.toLongOrNull() ?: -1L,
                    ) as T
            }
        }
    }
}
