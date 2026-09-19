package com.brandonmiller.audiobookplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ebook.Emphasis
import com.brandonmiller.audiobookplayer.ebook.EmphasisSpan
import com.brandonmiller.audiobookplayer.summaries.inlineMarkdown
import com.brandonmiller.audiobookplayer.ui.theme.AudiobookType
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors

/**
 * One imported chapter summary, read and dismissed (`add-chapter-summaries` design D9).
 *
 * A `ModalBottomSheet` because that is what every transient thing in this app already is — the
 * chapter list, the table of contents, the search field, the removal confirmation — and because a
 * summary is read once and put away rather than navigated to.
 *
 * Lives in the top-level `ui` package rather than under `player` or `reader`: both chapter lists open
 * the same sheet, and the end-of-chapter offer opens it with neither list on screen.
 *
 * The text is not selectable and not editable. The file is the source of truth; correcting a summary
 * means editing the file and importing it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(
    chapterTitle: String,
    text: String,
    onDismiss: () -> Unit,
) {
    val colors = audiobookColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

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
            // A bounded height rather than a fraction: a two-line summary should not open a sheet
            // covering the screen, and a long one should not run past the status bar.
            modifier = Modifier.heightIn(max = MAX_SHEET_HEIGHT),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTooltip(stringResource(R.string.summaries_close)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        CollapseIcon(
                            size = 26.dp,
                            color = colors.ink,
                            contentDescription = stringResource(R.string.summaries_close),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = chapterTitle,
                        style = AudiobookType.titleSheet,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.summaries_heading),
                        style = AudiobookType.monoMeta,
                        color = colors.textTertiary,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.track))

            // Annotated rather than raw, so a summary written in Markdown shows its emphasis instead
            // of the asterisks that expressed it. Done here rather than at import because the file
            // stays the source of truth: what is stored is what the owner wrote.
            Text(
                text = remember(text) { annotateSummary(text) },
                style = AudiobookType.bodyLarge,
                color = colors.inkMuted,
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            )
        }
    }
}

/**
 * A summary's Markdown emphasis as an [AnnotatedString], in the shape `ReaderScreen.annotate` uses
 * for the same [EmphasisSpan] type — one way of turning emphasis into styled text, not two.
 */
private fun annotateSummary(source: String): AnnotatedString {
    val inline = inlineMarkdown(source)
    return buildAnnotatedString {
        append(inline.text)
        inline.emphasis.forEach { span ->
            addStyle(
                SpanStyle(
                    fontStyle = if (Emphasis.Italic in span.styles) FontStyle.Italic else null,
                    fontWeight = if (Emphasis.Bold in span.styles) FontWeight.Bold else null,
                ),
                span.start,
                span.end,
            )
        }
    }
}

/** Tall enough for a long summary, short enough to leave the sheet reading as a sheet. */
private val MAX_SHEET_HEIGHT = 560.dp
