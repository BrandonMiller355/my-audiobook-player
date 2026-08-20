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

/**
 * An open book. Outlined when nothing is linked, with its pages filled when something is.
 *
 * The two forms exist so the control says which of two different things a tap does — open a file
 * picker, or open the reader — rather than leaving the user to find out (`add-ebook-companion`
 * design D15). Filling the pages rather than adding a badge keeps both forms the same silhouette,
 * so the difference reads at a glance without the icon changing size.
 */
@Composable
fun BookIcon(
    size: Dp,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        // Two leaves meeting at the spine, each a rectangle whose outer edge curves away.
        fun leaf(outerX: Float) = Path().apply {
            moveTo(12f * unit, 7f * unit)
            lineTo(outerX * unit, 5f * unit)
            lineTo(outerX * unit, 18f * unit)
            lineTo(12f * unit, 20f * unit)
            close()
        }

        for (outerX in listOf(3f, 21f)) {
            val path = leaf(outerX)
            if (filled) drawPath(path, color, alpha = FILLED_PAGE_ALPHA)
            drawPath(path, color, style = stroke)
        }

        // The spine, drawn last so it sits over both leaves' inner edges.
        drawLine(color, Offset(12f * unit, 7f * unit), Offset(12f * unit, 20f * unit), stroke.width, StrokeCap.Round)
    }
}

/** Enough to read as filled against the cover scrim without swallowing the stroke that defines it. */
private const val FILLED_PAGE_ALPHA = 0.45f

/** A circled exclamation, marking a book whose source has gone. */
@Composable
fun WarningIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        drawCircle(color, radius = 9f * unit, center = Offset(12f * unit, 12f * unit), style = stroke)
        drawLine(color, Offset(12f * unit, 8f * unit), Offset(12f * unit, 13f * unit), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(12f * unit, 16.5f * unit), Offset(12f * unit, 17f * unit), stroke.width, StrokeCap.Round)
    }
}

/** A magnifier, for searching an ebook's text. */
@Composable
fun SearchIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        drawCircle(color, radius = 6.5f * unit, center = Offset(10.5f * unit, 10.5f * unit), style = stroke)
        drawLine(color, Offset(15.5f * unit, 15.5f * unit), Offset(20f * unit, 20f * unit), stroke.width, StrokeCap.Round)
    }
}

/**
 * A numbered list — the reader's table of contents.
 *
 * Numbered rather than bulleted, and bulleted rather than barred, because the shape this replaced
 * was three equal full-width lines: a hamburger, which promises a navigation drawer and opens a
 * chapter list instead. Numerals say "ordered contents" in a way no arrangement of plain bars can,
 * and they are what the sheet behind the button actually shows.
 *
 * The numerals are drawn rather than set as text for the reason every shape in this file is: a font
 * glyph brings its own weight and optical size, which never agree with a stroke drawn to [GRID].
 * They carry a lighter stroke than the rest of the set — at full weight a numeral this small fills
 * in around its own curves — and sit on a 6.6-unit row pitch, wider than the lines alone would
 * need, because at the 6-unit pitch the three of them close up into a single vertical mass.
 */
@Composable
fun ContentsIcon(size: Dp, color: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    IconCanvas(size, contentDescription, modifier) { unit, stroke ->
        val numeralStroke = Stroke(width = 1.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Row centers, each paired with where its line ends — unequal lengths, so the right edge
        // stays ragged the way a list of chapter titles is.
        val rows = listOf(5.2f to 21f, 11.8f to 18f, 18.4f to 20f)

        rows.forEachIndexed { index, (y, lineEnd) ->
            drawLine(color, Offset(9.5f * unit, y * unit), Offset(lineEnd * unit, y * unit), stroke.width, StrokeCap.Round)
            drawPath(numeral(index + 1, cx = 4.3f * unit, cy = y * unit, unit = unit), color, style = numeralStroke)
        }
    }
}

/**
 * One of `1`, `2`, `3`, centered on ([cx], [cy]).
 *
 * Described around its own center at a nominal 5.6-unit height, then scaled to [NUMERAL_HEIGHT], so
 * the three share a single set of coordinates and resizing them is one constant.
 */
private fun numeral(value: Int, cx: Float, cy: Float, unit: Float): Path {
    val scale = NUMERAL_HEIGHT / 5.6f * unit
    fun x(value: Float) = cx + value * scale
    fun y(value: Float) = cy + value * scale
    return Path().apply {
        when (value) {
            1 -> {
                // Stem and flag, no foot: a foot would be the only serif in this set.
                moveTo(x(-1.2f), y(-1.5f))
                lineTo(x(0.15f), y(-2.8f))
                lineTo(x(0.15f), y(2.8f))
            }
            2 -> {
                moveTo(x(-1.5f), y(-1.6f))
                cubicTo(x(-1.3f), y(-3.5f), x(1.9f), y(-3.3f), x(1.5f), y(-1.1f))
                cubicTo(x(1.3f), y(0.4f), x(-0.5f), y(1.4f), x(-1.6f), y(2.8f))
                lineTo(x(1.7f), y(2.8f))
            }
            else -> {
                // Two bowls meeting just left of center, where a drawn 3 pinches.
                moveTo(x(-1.5f), y(-2.1f))
                cubicTo(x(0.3f), y(-3.6f), x(2.2f), y(-1.6f), x(0.1f), y(-0.15f))
                cubicTo(x(2.4f), y(0.1f), x(1.4f), y(3.3f), x(-1.5f), y(2.1f))
            }
        }
    }
}

/** Grid units. Tall enough to read as a numeral, short enough to clear the row above and below. */
private const val NUMERAL_HEIGHT = 4.6f

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
