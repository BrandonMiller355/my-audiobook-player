package com.brandonmiller.audiobookplayer.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import java.util.Locale

/** PRD §9's fixed speed stops — discrete values, not a continuum, hence a menu rather than a slider. */
private val SPEED_STOPS = listOf(
    0.75f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f,
)

private const val SEEK_SHORT_MS = 10_000L
private const val SEEK_LONG_MS = 60_000L

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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Asked once, at the moment the user first presses play — never at launch, so the app-shell
    // promise of no permission dialog on first launch still holds.
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Play either way. Refusal costs the notification, not the audio.
        viewModel.togglePlayPause()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    fun onPlayPause() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionAsked &&
            !state.isPlaying &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsNotificationPermission) {
            notificationPermissionAsked = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.togglePlayPause()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.bookTitle.ifBlank { stringResource(R.string.player_unknown_book) }) },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.connected) {
                Text(
                    text = stringResource(R.string.player_connecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = state.chapterTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                if (state.chapterCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.player_chapter_of,
                            state.chapterNumber,
                            state.chapterCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))

                BookScrubber(
                    positionMs = state.absolutePositionMs,
                    durationMs = state.bookDurationMs,
                    onSeek = viewModel::seekToAbsolute,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SeekButton(stringResource(R.string.player_seek_back_1m)) { viewModel.seekBy(-SEEK_LONG_MS) }
                    SeekButton(stringResource(R.string.player_seek_back_10s)) { viewModel.seekBy(-SEEK_SHORT_MS) }
                    PlayPauseButton(isPlaying = state.isPlaying, onClick = ::onPlayPause)
                    SeekButton(stringResource(R.string.player_seek_forward_10s)) { viewModel.seekBy(SEEK_SHORT_MS) }
                    SeekButton(stringResource(R.string.player_seek_forward_1m)) { viewModel.seekBy(SEEK_LONG_MS) }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    OutlinedButton(onClick = viewModel::previousChapter) {
                        Text(stringResource(R.string.player_previous_chapter))
                    }
                    OutlinedButton(onClick = viewModel::nextChapter) {
                        Text(stringResource(R.string.player_next_chapter))
                    }
                }

                Spacer(Modifier.height(16.dp))

                SpeedControl(speed = state.speed, onSpeedSelected = viewModel::setSpeed)
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onBack) {
                Text(stringResource(R.string.player_back))
            }
        }
    }
}

/**
 * Spans the whole book, not just the current chapter (PRD's book-wide scrubber). Dragging shows a
 * live target timestamp without seeking; the seek only commits on release.
 */
@Composable
private fun BookScrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayedPositionMs = dragPositionMs ?: positionMs
    val safeDuration = durationMs.coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedPositionMs.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            onValueChange = { dragPositionMs = it.toLong() },
            onValueChangeFinished = {
                dragPositionMs?.let(onSeek)
                dragPositionMs = null
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(displayedPositionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime((durationMs - displayedPositionMs).coerceAtLeast(0)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeekButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 56.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
    }
}

/** Deliberately large: PRD §21 wants this hittable while walking or running. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 88.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SpeedControl(speed: Float, onSpeedSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.player_speed_label, formatSpeed(speed)))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SPEED_STOPS.forEach { stop ->
                DropdownMenuItem(
                    text = { Text(formatSpeed(stop) + "x") },
                    onClick = {
                        onSpeedSelected(stop)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt()
    return if (rounded % 100 == 0) "${rounded / 100}" else String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
}

/** `h:mm:ss` for anything an hour or longer, `m:ss` below that. */
private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
