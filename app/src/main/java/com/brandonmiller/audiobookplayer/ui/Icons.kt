package com.brandonmiller.audiobookplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The redesign's icon set, drawn rather than taken from `androidx.compose.material.icons`.
 *
 * Two reasons, either sufficient. The design specifies every icon as a 2-unit stroke with round
 * caps and joins, and Material's default set is filled glyphs with no stroke to set a width on —
 * at a 40dp seek chevron that difference is most of the screen's character. And half of what is
 * needed (pause, folder, document, the double chevrons) is not in `material-icons-core` at all but
 * in `material-icons-extended`, which is a large artifact to add for four shapes, particularly
 * with `isMinifyEnabled = false` on release (design D2).
 *
 * Every shape is expressed against the same 24×24 grid the design's SVGs use, so their path
 * coordinates transcribe directly and can be checked against the handoff by reading across.
 * Stroke width is in grid units for the same reason, and therefore scales with the icon exactly as
 * the reference does. `contentDescription` stays on each one; nothing here is decorative.
 */
private const val GRID = 24f

/** The design's stroke weight for every outlined icon save the `+`, which is drawn heavier. */
private const val DEFAULT_STROKE_UNITS = 2f

enum class HorizontalDirection { Left, Right }

@Composable
fun ChevronIcon(
    direction: HorizontalDirection,
    size: Dp,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    doubled: Boolean = false,
) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        // A single chevron sits centered; a doubled one is two chevrons nine units apart, which
        // puts the pair in the same optical center as the single (handoff: M11/M20 pointing left,
        // M4/M13 pointing right).
        val tips = if (doubled) listOf(11f, 20f) else listOf(15f)
        for (tip in tips) {
            val path = Path().apply {
                // Mirrored across the grid's vertical center line for the right-facing form.
                fun x(value: Float) = (if (direction == HorizontalDirection.Left) value else GRID - value) * unit
                moveTo(x(tip), 5f * unit)
                lineTo(x(tip - 7f), 12f * unit)
                lineTo(x(tip), 19f * unit)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/** Points up. The chapter sheet's collapse affordance. */
@Composable
fun CollapseIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        val path = Path().apply {
            moveTo(6f * unit, 15f * unit)
            lineTo(12f * unit, 9f * unit)
            lineTo(18f * unit, 15f * unit)
        }
        drawPath(path, color, style = stroke)
    }
}

/** Heavier than the rest at 2.2 units — it is small, and needs the weight to hold at 22dp. */
@Composable
fun PlusIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier, strokeUnits = 2.2f) { unit, stroke ->
        drawLine(color, Offset(12f * unit, 5f * unit), Offset(12f * unit, 19f * unit), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(5f * unit, 12f * unit), Offset(19f * unit, 12f * unit), stroke.width, StrokeCap.Round)
    }
}

/**
 * Filled, not stroked, and nudged right by two units: a triangle centered on its bounding box
 * reads as sitting left of center, and every play button here is a circle, where that is obvious.
 */
@Composable
fun PlayIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, _ ->
        val nudge = 2f * unit
        val path = Path().apply {
            moveTo(8f * unit + nudge, 5f * unit)
            lineTo(20f * unit + nudge, 12f * unit)
            lineTo(8f * unit + nudge, 19f * unit)
            close()
        }
        drawPath(path, color)
    }
}

/** Two filled bars with a one-unit corner radius, matching the design's `rect rx="1"`. */
@Composable
fun PauseIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, _ ->
        for (left in listOf(7f, 14f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left * unit, 4f * unit),
                size = Size(4f * unit, 16f * unit),
                cornerRadius = CornerRadius(1f * unit, 1f * unit),
            )
        }
    }
}

@Composable
fun FolderIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        val path = Path().apply {
            moveTo(3f * unit, 7f * unit)
            lineTo(9f * unit, 7f * unit)
            lineTo(11f * unit, 9f * unit)
            lineTo(21f * unit, 9f * unit)
            lineTo(21f * unit, 19f * unit)
            lineTo(3f * unit, 19f * unit)
            close()
        }
        drawPath(path, color, style = stroke)
    }
}

/** A page with its corner turned — the single-file counterpart to [FolderIcon]. */
@Composable
fun DocumentIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        val page = Path().apply {
            moveTo(14f * unit, 3f * unit)
            lineTo(6f * unit, 3f * unit)
            lineTo(6f * unit, 21f * unit)
            lineTo(18f * unit, 21f * unit)
            lineTo(18f * unit, 7f * unit)
            close()
        }
        drawPath(page, color, style = stroke)

        val fold = Path().apply {
            moveTo(14f * unit, 3f * unit)
            lineTo(14f * unit, 7f * unit)
            lineTo(18f * unit, 7f * unit)
        }
        drawPath(fold, color, style = stroke)
    }
}

/** A circled exclamation, marking a book whose source has gone. */
@Composable
fun WarningIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        drawCircle(color, radius = 9f * unit, center = Offset(12f * unit, 12f * unit), style = stroke)
        drawLine(color, Offset(12f * unit, 8f * unit), Offset(12f * unit, 13f * unit), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(12f * unit, 16.5f * unit), Offset(12f * unit, 17f * unit), stroke.width, StrokeCap.Round)
    }
}

/**
 * The shared frame: a square canvas of [size], the pixel length of one grid unit, and a [Stroke]
 * already carrying the round caps and joins every icon here uses.
 */
@Composable
private fun IconCanvas(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    strokeUnits: Float = DEFAULT_STROKE_UNITS,
    draw: DrawScope.(unit: Float, stroke: Stroke) -> Unit,
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described.size(size)) {
        val unit = this.size.minDimension / GRID
        draw(unit, Stroke(width = strokeUnits * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
