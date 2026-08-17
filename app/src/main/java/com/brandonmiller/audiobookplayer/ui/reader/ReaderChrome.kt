package com.brandonmiller.audiobookplayer.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.NavEntry
import com.brandonmiller.audiobookplayer.ui.ChevronIcon
import com.brandonmiller.audiobookplayer.ui.HorizontalDirection
import com.brandonmiller.audiobookplayer.ui.PauseIcon
import com.brandonmiller.audiobookplayer.ui.PlayIcon

/**
 * The revealed controls: flip back, contents, settings, play/pause, and the two ebook-management
 * actions.
 *
 * One revealed layer rather than a fixed bar, because a permanent bar contradicts the point of a
 * black reading page, and because six controls need somewhere to live that a single corner button
 * cannot provide (`add-ebook-companion` design D7).
 */
@Composable
fun ReaderChrome(
    visible: Boolean,
    isPlaying: Boolean,
    hasContents: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
    onChange: () -> Unit,
    onUnlink: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // Fills the screen, not just its content: the two rows align to opposite edges, so a box
        // sized to its children would put the bottom row directly under the top one.
        Box(modifier = Modifier.fillMaxSize()) {
            // The controls sit over live prose, and without these they collide with it — the
            // bottom row worst, being words over words. The Player's cover solves the same problem
            // the same way, and for the same reason: one treatment that is safe over any content.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(SCRIM_HEIGHT)
                    .background(Brush.verticalGradient(listOf(ReaderScrim, Color.Transparent))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(SCRIM_HEIGHT)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ReaderScrim))),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeButton(onClick = onBack) {
                    ChevronIcon(
                        direction = HorizontalDirection.Left,
                        size = 24.dp,
                        color = ReaderChromeInk,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChromeButton(onClick = onPlayPause) {
                        if (isPlaying) {
                            PauseIcon(20.dp, ReaderChromeInk, stringResource(R.string.player_pause))
                        } else {
                            PlayIcon(20.dp, ReaderChromeInk, stringResource(R.string.player_play))
                        }
                    }
                    if (hasContents) {
                        ChromeButton(onClick = onContents) {
                            ContentsGlyph(stringResource(R.string.reader_contents))
                        }
                    }
                    ChromeButton(onClick = onSettings) {
                        Text(
                            text = "Aa",
                            color = ReaderChromeInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ReaderTextButton(stringResource(R.string.reader_change), onChange)
                ReaderTextButton(stringResource(R.string.reader_unlink), onUnlink)
            }
        }
    }
}

/** Three stacked lines. Drawn here rather than in `ui/Icons.kt` — nothing else needs it. */
@Composable
private fun ContentsGlyph(description: String) {
    Column(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ReaderChromeInk),
            )
        }
    }
}

@Composable
private fun ChromeButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ReaderChromeFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A word used as a control, for the low-frequency actions that do not warrant an icon.
 *
 * Filled rather than bare. These sit over live prose, and a scrim alone still leaves words on top
 * of words — the pill is what makes a control read as a control rather than as a line of the book.
 */
@Composable
fun ReaderTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = ReaderChromeInk,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ReaderChromeFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * The ebook's own table of contents, indented by depth so parts and the chapters under them stay
 * distinguishable (spike finding 2 — the owner's books nest).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    entries: List<NavEntry>,
    onDismiss: () -> Unit,
    onSelect: (NavEntry) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ReaderSheet,
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_contents_empty),
                color = ReaderChromeInkDim,
                fontSize = 15.sp,
                modifier = Modifier.padding(24.dp),
            )
            return@ModalBottomSheet
        }

        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            items(entries) { entry ->
                Text(
                    text = entry.label,
                    color = if (entry.depth == 0) ReaderChromeInk else ReaderChromeInkDim,
                    fontSize = if (entry.depth == 0) 16.sp else 15.sp,
                    fontWeight = if (entry.depth == 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) }
                        .padding(
                            start = 24.dp + (entry.depth * 16).dp,
                            end = 24.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Text size, line spacing, typeface, and brightness.
 *
 * Steppers rather than sliders: each of these has a small number of useful values, and a stepper
 * can be hit without looking, which is the same argument the Player's speed chips answer to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    settings: ReadingSettings,
    onDismiss: () -> Unit,
    onTextScale: (Float) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onSerif: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ReaderSheet,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Stepper(
                label = stringResource(R.string.reader_text_size),
                value = "${(settings.textScale * 100).toInt()}%",
                decreaseLabel = stringResource(R.string.reader_smaller),
                increaseLabel = stringResource(R.string.reader_larger),
                onDecrease = { onTextScale(settings.textScale - ReadingSettings.TEXT_SCALE_STEP) },
                onIncrease = { onTextScale(settings.textScale + ReadingSettings.TEXT_SCALE_STEP) },
            )

            Stepper(
                label = stringResource(R.string.reader_line_spacing),
                value = String.format("%.1f", settings.lineSpacing),
                decreaseLabel = stringResource(R.string.reader_tighter),
                increaseLabel = stringResource(R.string.reader_looser),
                onDecrease = { onLineSpacing(settings.lineSpacing - ReadingSettings.LINE_SPACING_STEP) },
                onIncrease = { onLineSpacing(settings.lineSpacing + ReadingSettings.LINE_SPACING_STEP) },
            )

            Stepper(
                label = stringResource(R.string.reader_brightness),
                value = if (settings.followsSystemBrightness) {
                    "—"
                } else {
                    "${(settings.brightness * 100).toInt()}%"
                },
                decreaseLabel = stringResource(R.string.reader_dimmer),
                increaseLabel = stringResource(R.string.reader_brighter),
                // The first press has to start somewhere: from the system's brightness there is no
                // number to step from, so it enters the range at full and steps down from there.
                onDecrease = {
                    val from = if (settings.followsSystemBrightness) 1f else settings.brightness
                    onBrightness(from - ReadingSettings.BRIGHTNESS_STEP)
                },
                onIncrease = {
                    val from = if (settings.followsSystemBrightness) 1f else settings.brightness
                    onBrightness(from + ReadingSettings.BRIGHTNESS_STEP)
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reader_typeface), color = ReaderChromeInk, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypefaceChip(stringResource(R.string.reader_typeface_serif), settings.serif) { onSerif(true) }
                    TypefaceChip(stringResource(R.string.reader_typeface_sans), !settings.serif) { onSerif(false) }
                }
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, color = ReaderChromeInk, fontSize = 15.sp)
            Text(value, color = ReaderChromeInkDim, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChromeButton(onClick = onDecrease) {
                ChevronIcon(HorizontalDirection.Left, 20.dp, ReaderChromeInk, decreaseLabel)
            }
            ChromeButton(onClick = onIncrease) {
                ChevronIcon(HorizontalDirection.Right, 20.dp, ReaderChromeInk, increaseLabel)
            }
        }
    }
}

@Composable
private fun TypefaceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) ReaderSheet else ReaderChromeInk,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ReaderChromeInk else ReaderChromeFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * The chrome's own palette, a step off the page's pure black.
 *
 * The controls have to be legible without competing with the text they sit over, which is why these
 * are not the page's `ReaderInk`: a pure-white control on pure black at the edge of vision is the
 * thing the eye keeps going back to.
 */
private val ReaderChromeInk = Color(0xFFF2F2F2)
private val ReaderChromeInkDim = Color(0xFF9A9A9A)
private val ReaderChromeFill = Color(0x33FFFFFF)

/** Sheets sit one step off the page, which is what makes them read as lifted from it. */
private val ReaderSheet = Color(0xFF121212)

/** Behind the chrome, fading to nothing, so the controls never have to compete with the prose. */
private val ReaderScrim = Color(0xE6000000)
private val SCRIM_HEIGHT = 180.dp
