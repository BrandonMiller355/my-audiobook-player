package com.brandonmiller.audiobookplayer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ui.CollapseIcon
import com.brandonmiller.audiobookplayer.ui.IconTooltip
import com.brandonmiller.audiobookplayer.ui.SummaryIcon
import com.brandonmiller.audiobookplayer.ui.formatChapterLength
import com.brandonmiller.audiobookplayer.ui.formatSpeed
import com.brandonmiller.audiobookplayer.ui.theme.AudiobookType
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors
import java.util.Locale

/**
 * Chapters and speed, inline over the Player rather than on a screen of their own.
 *
 * One sheet with two sections rather than two sheets: speed and chapter are the two things adjusted
 * mid-walk, and putting them behind one gesture means the user learns one place instead of two
 * (design D7). [initialSection] decides only what the sheet scrolls to on open; both sections are
 * always present.
 *
 * Playback stays running and stays controllable throughout — the header carries its own play/pause,
 * and selecting a speed does not dismiss anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(
    state: PlayerUiState,
    initialSection: SheetSection,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onChapterSelected: (PlayerChapter) -> Unit,
    onImportSummaries: () -> Unit,
    onSummarySelected: (PlayerChapter) -> Unit,
) {
    val colors = audiobookColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val speedState = rememberLazyListState()

    // Both effects run once, when the sheet opens. Keying them on the values they read would make
    // the chapter list re-scroll as playback advances and the chip row jump under the finger that
    // just tapped it — in a sheet whose whole purpose is adjusting those two things.
    LaunchedEffect(Unit) {
        if (state.chapters.isNotEmpty()) {
            // The current chapter lands as the second visible row, so the one before it stays in
            // view: going back one is the more common correction and should not need a scroll.
            listState.scrollToItem((state.chapterNumber - 2).coerceAtLeast(0))
        }
        SPEED_STOPS.indexOfFirst { it == state.speed }
            .takeIf { it >= 0 }
            ?.let { speedState.scrollToItem((it - 1).coerceAtLeast(0)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null,
        scrimColor = colors.scrim,
    ) {
        Column(modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            MiniPlayerHeader(
                bookTitle = state.bookTitle,
                chapterCount = state.chapterCount,
                isPlaying = state.isPlaying,
                onCollapse = onDismiss,
                onPlayPause = onPlayPause,
            )

            SpeedRow(
                speed = state.speed,
                listState = speedState,
                onSpeedSelected = onSpeedSelected,
            )

            SummariesRow(
                matchedCount = state.summaries.size,
                chapterCount = state.chapterCount,
                errorText = state.summaryImportError,
                onImport = onImportSummaries,
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(state.chapters, key = { it.number }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        isCurrent = chapter.number == state.chapterNumber,
                        remainingMs = state.chapterRemainingMs,
                        // A row's number is its index plus one; the summaries are keyed by index.
                        hasSummary = state.summaries.containsKey(chapter.number - 1),
                        onClick = { onChapterSelected(chapter) },
                        onSummaryClick = { onSummarySelected(chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerHeader(
    bookTitle: String,
    chapterCount: Int,
    isPlaying: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val colors = audiobookColors

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTooltip(stringResource(R.string.player_sheet_collapse)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onCollapse),
                    contentAlignment = Alignment.Center,
                ) {
                    CollapseIcon(
                        size = 26.dp,
                        color = colors.ink,
                        contentDescription = stringResource(R.string.player_sheet_collapse),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_chapters),
                    style = AudiobookType.titleSheet,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.player_sheet_subtitle, bookTitle, chapterCount)
                        .uppercase(Locale.getDefault()),
                    style = AudiobookType.monoMeta,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PlayPauseButton(
                isPlaying = isPlaying,
                enabled = true,
                size = 52.dp,
                iconSize = 20.dp,
                onClick = onPlayPause,
            )
        }

        Hairline()
    }
}

/**
 * Every stop in one horizontally scrollable row. This replaces the dropdown the Player used to
 * carry: a menu hides which speeds exist and closes on every choice, and adjusting speed is
 * something done by feel against what was just heard, often twice in a row.
 */
@Composable
private fun SpeedRow(
    speed: Float,
    listState: LazyListState,
    onSpeedSelected: (Float) -> Unit,
) {
    Column {
        // Lazy so the chip to scroll to can be named by index; the chips differ in width — "0.9"
        // against "0.75" — so a computed scroll offset would have been a guess.
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(SPEED_STOPS) { stop ->
                SpeedChip(
                    stop = stop,
                    selected = stop == speed,
                    onClick = { onSpeedSelected(stop) },
                )
            }
        }

        Hairline()
    }
}

@Composable
private fun SpeedChip(stop: Float, selected: Boolean, onClick: () -> Unit) {
    val colors = audiobookColors
    // Animated so the selection moving between chips reads as one thing changing rather than two
    // separate repaints.
    val background by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.fillOnRaised,
        animationSpec = tween(150),
        label = "chipBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onInk else colors.inkMuted,
        animationSpec = tween(150),
        label = "chipContent",
    )
    val label = formatSpeed(stop)
    // "1.25" alone tells a screen reader nothing about what it selects.
    val spoken = stringResource(R.string.player_sheet_speed, label)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            style = AudiobookType.monoChip,
            color = content,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        )
    }
}

/**
 * What the book carries, and the one control that changes it (`add-chapter-summaries` design D8).
 *
 * The count is the import's report. A message saying "42 of 90 matched" is gone in four seconds, and
 * that figure is exactly what is wanted when checking whether a file was numbered the way the audio
 * is — so it lives here, where it is still true tomorrow.
 *
 * A text button rather than an icon: this is pressed rarely, and "import a file of summaries" is not
 * a thing a glyph can say.
 *
 * [errorText] is why an import came to nothing, and it is stated here rather than in a snackbar
 * because a snackbar is not visible from here at all: this sheet is a `ModalBottomSheet` and renders
 * above the `Scaffold` that hosts one. Device testing caught that; it had been failing silently.
 */
@Composable
private fun SummariesRow(
    matchedCount: Int,
    chapterCount: Int,
    errorText: String?,
    onImport: () -> Unit,
) {
    val colors = audiobookColors

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.summaries_heading),
                    style = AudiobookType.bodyLarge,
                    color = colors.inkMuted,
                )
                Text(
                    text = if (matchedCount == 0) {
                        stringResource(R.string.summaries_none)
                    } else {
                        stringResource(R.string.summaries_count, matchedCount, chapterCount)
                    }.uppercase(Locale.getDefault()),
                    style = AudiobookType.monoMeta,
                    color = colors.textTertiary,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.fillOnRaised)
                    .clickable(onClick = onImport)
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                Text(
                    text = stringResource(
                        if (matchedCount == 0) R.string.summaries_import else R.string.summaries_replace,
                    ),
                    style = AudiobookType.monoChip,
                    color = colors.inkMuted,
                )
            }
        }

        // Below the row rather than inside it, so a sentence can wrap to the sheet's full width
        // instead of being squeezed beside the button that produced it.
        if (errorText != null) {
            Text(
                text = errorText,
                style = AudiobookType.bodyLarge,
                color = colors.error,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            )
        }

        Hairline()
    }
}

/**
 * The current chapter's row inverts and bleeds to both edges, and swaps its total length for the
 * time left in it — on the one row where "how long is this chapter" is the less useful of the two.
 */
@Composable
private fun ChapterRow(
    chapter: PlayerChapter,
    isCurrent: Boolean,
    remainingMs: Long?,
    hasSummary: Boolean,
    onClick: () -> Unit,
    onSummaryClick: () -> Unit,
) {
    val colors = audiobookColors
    val trailingMs = if (isCurrent) remainingMs ?: chapter.durationMs else chapter.durationMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) colors.ink else colors.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = if (hasSummary) 8.dp else 22.dp)
            .padding(vertical = if (isCurrent) 18.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = chapter.number.toString(),
            style = AudiobookType.monoNumber,
            color = colors.textQuaternary,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = chapter.title,
            style = if (isCurrent) AudiobookType.titleRow else AudiobookType.bodyLarge,
            color = if (isCurrent) colors.onInk else colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            // Blank rather than a zero: a folder chapter whose duration has not resolved has no
            // length to state, and "0:00" would be a claim about it.
            text = trailingMs?.let(::formatChapterLength).orEmpty(),
            style = AudiobookType.monoMeta,
            color = if (isCurrent) colors.onInkMuted else colors.textQuaternary,
        )

        // Its own clickable, inside the row's: a tap here opens the summary and must not select the
        // chapter, which would move playback (`add-chapter-summaries` design D8). Absent rather than
        // disabled on a chapter with no summary — there is nothing to explain.
        if (hasSummary) {
            val label = stringResource(R.string.summaries_open)
            IconTooltip(label) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onSummaryClick),
                    contentAlignment = Alignment.Center,
                ) {
                    SummaryIcon(
                        size = 20.dp,
                        // The current row is inverted, so its content has to invert with it.
                        color = if (isCurrent) colors.onInk else colors.inkMuted,
                        contentDescription = label,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(audiobookColors.track))
}

/**
 * Tall enough to read as covering the Player, short enough that the mini-player header sits below
 * the status bar rather than under it.
 */
private const val SHEET_HEIGHT_FRACTION = 0.92f
