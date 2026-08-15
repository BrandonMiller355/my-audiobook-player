package com.brandonmiller.audiobookplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.brandonmiller.audiobookplayer.R
import java.io.File

/**
 * A book's cached cover, or the placeholder when it has none — the one place that decision is made,
 * so the library list and the Player cannot disagree about what a coverless book looks like.
 *
 * Every failure resolves to the placeholder rather than to an error: a book whose cover file has
 * been deleted from underneath the app, or whose artwork was never cached, stays fully usable.
 * Loading is asynchronous and off the main thread, which is what keeps a scrolling library of
 * covers responsive.
 */
@Composable
fun BookCover(artworkPath: String?, size: Dp, modifier: Modifier = Modifier) {
    val placeholder = painterResource(R.drawable.ic_cover_placeholder)
    val description = stringResource(R.string.library_cover)
    val shaped = modifier.size(size).clip(MaterialTheme.shapes.small)

    if (artworkPath == null) {
        Image(
            painter = placeholder,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    } else {
        AsyncImage(
            model = File(artworkPath),
            contentDescription = description,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    }
}

/** The library list's thumbnail size, matched to a two-line row. */
val LibraryCoverSize: Dp = 56.dp

/** The Player's cover, large enough to be the screen's anchor without crowding the controls. */
val PlayerCoverSize: Dp = 200.dp
