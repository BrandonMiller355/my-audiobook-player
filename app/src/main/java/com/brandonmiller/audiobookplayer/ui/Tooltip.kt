package com.brandonmiller.audiobookplayer.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/**
 * Every icon-only control in the app gets one of these. An icon with no visible label has nothing
 * to name it for a sighted user who has not yet learned what it does by tapping it — the same gap
 * `contentDescription` closes for a screen reader, but for someone who is not using one.
 *
 * Long-press only (`TooltipBox`'s own gesture handling): a tap still reaches [content]'s own
 * `clickable` untouched, so this adds a label without changing what a normal tap does.
 *
 * Standing instruction: every new icon-only control gets one of these, with the same string already
 * passed to the icon's `contentDescription` — one label serves both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}
