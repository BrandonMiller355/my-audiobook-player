package com.brandonmiller.audiobookplayer.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ui.BookIcon
import com.brandonmiller.audiobookplayer.ui.ChevronIcon
import com.brandonmiller.audiobookplayer.ui.library.OpenPersistableDocument
import com.brandonmiller.audiobookplayer.ui.FullBleedBookCover
import com.brandonmiller.audiobookplayer.ui.HorizontalDirection
import com.brandonmiller.audiobookplayer.ui.PLAYER_COVER_MAX_HEIGHT_FRACTION
import com.brandonmiller.audiobookplayer.ui.PauseIcon
import com.brandonmiller.audiobookplayer.ui.PlayIcon
import com.brandonmiller.audiobookplayer.ui.formatSpeed
import com.brandonmiller.audiobookplayer.ui.formatTime
import com.brandonmiller.audiobookplayer.ui.theme.AudiobookType
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors
import java.util.Locale
import kotlin.math.roundToLong

/** PRD §9's fixed speed stops — discrete values, not a continuum, hence chips rather than a slider. */
internal val SPEED_STOPS = listOf(
    0.75f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f,
)

private const val SEEK_SHORT_MS = 10_000L
private const val SEEK_LONG_MS = 60_000L

/** Which half of the footer opened the sheet, and therefore what it should scroll to. */
internal enum class SheetSection { Chapters, Speed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(LocalContext.current, bookId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = audiobookColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pickEbook = rememberLauncherForActivityResult(OpenPersistableDocument()) { uri ->
        uri?.let(viewModel::linkEbook)
    }

    // Picking an ebook goes straight on into reading it, rather than returning the user to the
    // cover to press the same icon a second time.
    LaunchedEffect(state.openReaderRequested) {
        if (state.openReaderRequested) {
            viewModel.consumeReaderRequest()
            onOpenReader()
        }
    }

    // Local, not ViewModel state: it has no meaning outside the sheet's own lifetime, and putting
    // it on the ViewModel would make the ViewModel responsible for a scroll position (design D7).
    var openSection by remember { mutableStateOf<SheetSection?>(null) }

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

    LightStatusBarIcons()

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
        containerColor = colors.surface,
        // The cover runs edge to edge behind the status bar, so the Scaffold must not reserve
        // room for it; the inset is applied by the back button alone.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PlayerCover(
                artworkPath = state.artworkPath,
                bookId = state.bookId,
                hasEbook = state.hasEbook,
                onBack = onBack,
                onEbook = {
                    if (state.hasEbook) {
                        onOpenReader()
                    } else {
                        pickEbook.launch(OpenPersistableDocument.EBOOK_MIME_TYPES)
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp)
                    // Disabled rather than hidden: the layout is the same before and after the
                    // session connects, so nothing jumps when it does.
                    .alpha(if (state.connected) 1f else DISABLED_ALPHA),
            ) {
                Text(
                    text = when {
                        !state.connected -> stringResource(R.string.player_connecting)
                        else -> state.bookTitle.ifBlank { stringResource(R.string.player_unknown_book) }
                    },
                    style = if (state.connected) AudiobookType.titlePlayer else AudiobookType.bodyEmpty,
                    color = if (state.connected) colors.ink else colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.chapterCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.player_chapter_line,
                            state.chapterNumber,
                            state.chapterCount,
                            state.chapterTitle,
                        ).uppercase(Locale.getDefault()),
                        style = AudiobookType.monoCaps,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(22.dp))

                BookScrubber(
                    positionMs = state.absolutePositionMs,
                    durationMs = state.bookDurationMs,
                    enabled = state.connected,
                    onSeek = viewModel::seekToAbsolute,
                )

                TransportRow(
                    isPlaying = state.isPlaying,
                    enabled = state.connected,
                    modifier = Modifier.weight(1f),
                    onPlayPause = ::onPlayPause,
                    onSeekBy = viewModel::seekBy,
                )
            }

            PlayerFooter(
                chapterNumber = state.chapterNumber,
                chapterCount = state.chapterCount,
                speed = state.speed,
                enabled = state.connected,
                onOpen = { openSection = it },
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    openSection?.let { section ->
        ChaptersSheet(
            state = state,
            initialSection = section,
            onDismiss = { openSection = null },
            onPlayPause = ::onPlayPause,
            onSpeedSelected = viewModel::setSpeed,
            onChapterSelected = { chapter ->
                viewModel.seekToAbsolute(chapter.startMs)
                openSection = null
            },
        )
    }
}

/**
 * Light status-bar icons for as long as the Player is shown, in both themes.
 *
 * The cover runs behind the status bar under a 45–50% dark scrim; the scrim exists precisely so
 * that one choice is safe over any artwork, which is what lets this be a constant rather than
 * something derived from the image. Restored on the way out, or the Library's dark-on-light header
 * would be left under light icons (design D8).
 */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return

    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/**
 * The hero: square, full width, no radius, drawn behind the status bar.
 *
 * The height cap is the one departure from `width × width`. On a short device a full-width square
 * pushes the transport row off the bottom, and a seek control the user has to scroll to find is one
 * they cannot hit without looking — which is the whole premise of the design (design D9).
 */
@Composable
private fun PlayerCover(
    artworkPath: String?,
    bookId: Long,
    hasEbook: Boolean,
    onBack: () -> Unit,
    onEbook: () -> Unit,
) {
    val colors = audiobookColors

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val side = minOf(maxWidth, maxHeight * PLAYER_COVER_MAX_HEIGHT_FRACTION)

        Box(modifier = Modifier.width(maxWidth).height(side)) {
            FullBleedBookCover(
                artworkPath = artworkPath,
                tintSeed = bookId,
                modifier = Modifier.fillMaxSize(),
            )

            // Top scrim: keeps the status bar and the back button legible on any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.coverScrim,
                            0.34f to Color.Transparent,
                        ),
                    ),
            )

            // Dark only. Without it a bright cover's bottom edge reads as a seam against the dark
            // surround rather than as the end of the artwork.
            colors.coverBaseFade?.let { fade ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, fade))),
                )
            }

            // Back and the ebook control share the same disc treatment on the same top scrim; the
            // scrim exists so one styling is safe over any artwork, which is what lets a second
            // control join it without its own.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoverButton(onClick = onBack) {
                    ChevronIcon(
                        direction = HorizontalDirection.Left,
                        size = 24.dp,
                        color = colors.onInk,
                        contentDescription = stringResource(R.string.player_back),
                    )
                }
                CoverButton(onClick = onEbook) {
                    BookIcon(
                        size = 24.dp,
                        color = colors.onInk,
                        filled = hasEbook,
                        contentDescription = stringResource(
                            if (hasEbook) R.string.ebook_open else R.string.ebook_link,
                        ),
                    )
                }
            }
        }
    }
}

/** The 44dp disc both cover controls sit in. */
@Composable
private fun CoverButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(audiobookColors.backScrim)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Spans the whole book, not just the current chapter (PRD's book-wide scrubber). Dragging shows a
 * live target timestamp without seeking; the seek only commits on release.
 *
 * Drawn rather than a restyled `Slider`: the design's thumb is one fixed 20dp circle with a 4dp
 * ring in the surface color and no state layer, and Material's thumb grows on press by design
 * (design D6). The semantics below are what keeps it operable without one.
 */
@Composable
private fun BookScrubber(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    val colors = audiobookColors
    val density = LocalDensity.current
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var trackWidthPx by remember { mutableStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1)
    val displayedPositionMs = (dragPositionMs ?: positionMs).coerceIn(0, safeDuration)
    val fraction = displayedPositionMs.toFloat() / safeDuration
    val description = stringResource(R.string.player_scrubber)

    // The thumb is centered on the position, so at either end half of it would hang outside the
    // control. Insetting the travel by its radius keeps the whole thumb on screen at 0 and at 100%,
    // which is the same thing a Slider does and the reason its track is not the full width.
    val thumbRadiusPx = with(density) { 10.dp.toPx() }
    val travelPx = (trackWidthPx - 2 * thumbRadiusPx).coerceAtLeast(1f)

    fun positionFor(x: Float): Long =
        (((x - thumbRadiusPx) / travelPx).coerceIn(0f, 1f) * safeDuration).roundToLong()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // The touch area is taller than the 8dp track so the control clears the 48dp
                // minimum without the track itself growing.
                .height(48.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = displayedPositionMs.toFloat(),
                        range = 0f..safeDuration.toFloat(),
                    )
                    if (enabled) {
                        setProgress { target ->
                            onSeek(target.roundToLong().coerceIn(0, safeDuration))
                            true
                        }
                    }
                }
                .pointerInput(enabled, safeDuration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragPositionMs = positionFor(offset.x) },
                        onDragEnd = {
                            dragPositionMs?.let(onSeek)
                            dragPositionMs = null
                        },
                        onDragCancel = { dragPositionMs = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragPositionMs = positionFor(change.position.x)
                        },
                    )
                }
                .pointerInput(enabled, safeDuration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> onSeek(positionFor(offset.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                val trackHeight = 8.dp.toPx()
                val radius = CornerRadius(trackHeight / 2, trackHeight / 2)
                val top = (size.height - trackHeight) / 2
                val thumbX = thumbRadiusPx + travelPx * fraction

                drawRoundRect(
                    color = colors.track,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackHeight),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = colors.ink,
                    topLeft = Offset(0f, top),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = radius,
                )

                // The ring is the surface color rather than a stroke, so the thumb reads as sitting
                // on the screen rather than as a circle with an outline.
                val center = Offset(thumbX, size.height / 2)
                drawCircle(colors.surface, radius = thumbRadiusPx, center = center)
                drawCircle(colors.ink, radius = thumbRadiusPx - 4.dp.toPx(), center = center)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(displayedPositionMs),
                style = AudiobookType.monoTime,
                color = colors.ink,
            )
            Text(
                // The minus is what separates this from a second elapsed reading; without it the
                // two figures are indistinguishable at a glance.
                text = "−" + formatTime((durationMs - displayedPositionMs).coerceAtLeast(0)),
                style = AudiobookType.monoTime,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * One 104dp play target with plain icon-only seek controls either side, and a caption row beneath
 * whose columns match the controls' widths so each caption sits under its own control.
 *
 * The captions are labels, not tap targets — the controls above carry the content descriptions, so
 * assistive technology reads one thing per control rather than an icon and an orphaned "10S".
 */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    val colors = audiobookColors

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekControl(
                box = 52.dp,
                icon = 32.dp,
                direction = HorizontalDirection.Left,
                doubled = true,
                color = colors.inkMuted,
                enabled = enabled,
                contentDescription = stringResource(R.string.player_seek_back_1m),
                onClick = { onSeekBy(-SEEK_LONG_MS) },
            )
            SeekControl(
                box = 60.dp,
                icon = 40.dp,
                direction = HorizontalDirection.Left,
                doubled = false,
                color = colors.ink,
                enabled = enabled,
                contentDescription = stringResource(R.string.player_seek_back_10s),
                onClick = { onSeekBy(-SEEK_SHORT_MS) },
            )
            PlayPauseButton(
                isPlaying = isPlaying,
                enabled = enabled,
                size = 104.dp,
                iconSize = 40.dp,
                onClick = onPlayPause,
            )
            SeekControl(
                box = 60.dp,
                icon = 40.dp,
                direction = HorizontalDirection.Right,
                doubled = false,
                color = colors.ink,
                enabled = enabled,
                contentDescription = stringResource(R.string.player_seek_forward_10s),
                onClick = { onSeekBy(SEEK_SHORT_MS) },
            )
            SeekControl(
                box = 52.dp,
                icon = 32.dp,
                direction = HorizontalDirection.Right,
                doubled = true,
                color = colors.inkMuted,
                enabled = enabled,
                contentDescription = stringResource(R.string.player_seek_forward_1m),
                onClick = { onSeekBy(SEEK_LONG_MS) },
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SeekCaption(stringResource(R.string.player_caption_1m), 52.dp)
            SeekCaption(stringResource(R.string.player_caption_10s), 60.dp)
            Spacer(Modifier.width(104.dp))
            SeekCaption(stringResource(R.string.player_caption_10s), 60.dp)
            SeekCaption(stringResource(R.string.player_caption_1m), 52.dp)
        }
    }
}

/**
 * Hidden from assistive technology on purpose: the control above already announces "Back ten
 * seconds", and leaving this visible would have a screen reader follow it with an orphaned "10S".
 */
@Composable
private fun SeekCaption(text: String, width: Dp) {
    Text(
        text = text,
        style = AudiobookType.monoMicro,
        color = audiobookColors.textQuaternary,
        modifier = Modifier.width(width).clearAndSetSemantics { },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SeekControl(
    box: Dp,
    icon: Dp,
    direction: HorizontalDirection,
    doubled: Boolean,
    color: Color,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChevronIcon(
            direction = direction,
            size = icon,
            color = color,
            doubled = doubled,
            contentDescription = contentDescription,
        )
    }
}

/**
 * Deliberately large: PRD §21 wants this hittable while walking or running.
 *
 * A `Surface` rather than a `Button` because Material's button cannot be a 104dp circle without
 * fighting its own content padding. The icon crossfades and the circle does not resize — a control
 * that changes size under the thumb is one that gets mis-hit.
 */
@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val colors = audiobookColors

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.ink,
        contentColor = colors.onInk,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(targetState = isPlaying, animationSpec = tween(120), label = "playPause") { playing ->
                if (playing) {
                    PauseIcon(iconSize, colors.onInk, stringResource(R.string.player_pause))
                } else {
                    PlayIcon(iconSize, colors.onInk, stringResource(R.string.player_play))
                }
            }
        }
    }
}

/** Two equal halves split by a hairline, each opening the same sheet at its own section. */
@Composable
private fun PlayerFooter(
    chapterNumber: Int,
    chapterCount: Int,
    speed: Float,
    enabled: Boolean,
    onOpen: (SheetSection) -> Unit,
) {
    val colors = audiobookColors

    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.track))

        // IntrinsicSize.Min so the vertical hairline can match the halves' height, which is set by
        // their content rather than by the row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        ) {
            FooterHalf(
                label = stringResource(R.string.player_chapters),
                subLabel = stringResource(R.string.player_chapter_position, chapterNumber, chapterCount),
                labelStyle = AudiobookType.labelAction,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(SheetSection.Chapters) },
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.track))
            FooterHalf(
                label = stringResource(R.string.player_speed_value, formatSpeed(speed)),
                subLabel = stringResource(R.string.player_speed_caption),
                labelStyle = AudiobookType.monoSpeed,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(SheetSection.Speed) },
            )
        }
    }
}

@Composable
private fun FooterHalf(
    label: String,
    subLabel: String,
    labelStyle: TextStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = audiobookColors

    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, style = labelStyle, color = colors.ink)
        Text(text = subLabel, style = AudiobookType.monoMicro, color = colors.textQuaternary)
    }
}

/** What a control fades to before the session has connected. Matches the design's 0.4. */
internal const val DISABLED_ALPHA = 0.4f
