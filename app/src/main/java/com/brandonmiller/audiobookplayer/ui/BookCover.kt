package com.brandonmiller.audiobookplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.ui.theme.audiobookColors
import java.io.File

/**
 * A book's cached cover, or the placeholder when it has none — the one place that decision is made,
 * so the library list and the Player cannot disagree about what a coverless book looks like.
 *
 * Every failure resolves to the placeholder rather than to an error: a book whose cover file has
 * been deleted from underneath the app, or whose artwork was never cached, stays fully usable.
 * Loading is asynchronous and off the main thread, which is what keeps a scrolling library of
 * covers responsive.
 *
 * [tintSeed] picks between the two placeholder tints. Passing the book's id means a shelf of
 * coverless books alternates rather than reading as one flat block, and that a given book keeps
 * the same tint across restarts instead of shuffling with its list position.
 */
@Composable
fun BookCover(
    artworkPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    tintSeed: Long = 0,
) {
    BookCoverContent(
        artworkPath = artworkPath,
        tintSeed = tintSeed,
        modifier = modifier.size(size).clip(RoundedCornerShape(CoverRadius)),
    )
}

/**
 * The Player's hero cover: square, full device width, no radius, drawn behind the status bar. It
 * takes a [Modifier] rather than a size because its dimensions come from the layout it fills
 * rather than from a constant.
 */
@Composable
fun FullBleedBookCover(artworkPath: String?, modifier: Modifier = Modifier, tintSeed: Long = 0) {
    BookCoverContent(artworkPath = artworkPath, tintSeed = tintSeed, modifier = modifier)
}

@Composable
private fun BookCoverContent(artworkPath: String?, tintSeed: Long, modifier: Modifier) {
    val colors = audiobookColors
    val placeholder = painterResource(R.drawable.ic_cover_placeholder)
    val description = stringResource(R.string.library_cover)
    val tint = if (tintSeed % 2 == 0L) colors.coverPlaceholder1 else colors.coverPlaceholder2

    if (artworkPath == null) {
        Image(
            painter = placeholder,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(tint),
        )
    } else {
        AsyncImage(
            model = File(artworkPath),
            contentDescription = description,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(tint),
        )
    }
}

/**
 * The thumb for a book whose source has gone. It replaces the cover rather than overlaying it: the
 * artwork is still cached and would render perfectly, which would be the one misleading thing to
 * show about a book that cannot currently be played.
 */
@Composable
fun UnavailableBookCover(size: Dp, modifier: Modifier = Modifier) {
    val colors = audiobookColors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(CoverRadius))
            .background(colors.errorTint),
        contentAlignment = Alignment.Center,
    ) {
        WarningIcon(size = 22.dp, color = colors.error, contentDescription = null)
    }
}

/** Every cover thumb's corner, from the resume card down to a list row. */
val CoverRadius: Dp = 6.dp

/** The library list's thumbnail, matched to the redesign's two-line row. */
val LibraryCoverSize: Dp = 64.dp

/** The resume card's cover — large enough to anchor the card without crowding its right column. */
val ResumeCoverSize: Dp = 132.dp

/**
 * A ceiling on the Player's cover as a fraction of screen height. The cover is `width × width` and
 * on a short device that would push the transport row off-screen, which the design forbids outright
 * — a seek control you have to scroll to find is one you cannot hit without looking (design D9).
 */
const val PLAYER_COVER_MAX_HEIGHT_FRACTION = 0.46f
