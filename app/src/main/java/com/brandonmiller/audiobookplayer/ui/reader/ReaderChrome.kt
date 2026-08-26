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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Ebook
import com.brandonmiller.audiobookplayer.ebook.NavEntry
import com.brandonmiller.audiobookplayer.ebook.SearchHit
import com.brandonmiller.audiobookplayer.ui.ChevronIcon
import com.brandonmiller.audiobookplayer.ui.HorizontalDirection
import com.brandonmiller.audiobookplayer.ui.IconTooltip
import com.brandonmiller.audiobookplayer.ui.OverflowIcon
import com.brandonmiller.audiobookplayer.ui.PauseIcon
import com.brandonmiller.audiobookplayer.ui.PlayIcon
import com.brandonmiller.audiobookplayer.ui.SearchIcon

/**
 * The revealed controls: flip back, play/pause, and a menu holding everything else.
 *
 * One revealed layer rather than a fixed bar, because a permanent bar contradicts the point of a
 * black reading page (`add-ebook-companion` design D7). What has changed since that design is where
 * the rest of the actions live. Six controls spread across two edges — four icons at the top, two
 * words at the bottom — meant scrims down both ends of the page and a row of words sitting over the
 * prose, for actions a reader touches once a session at most. They are now behind one overflow
 * button, which leaves the page with a single strip of chrome at the top and nothing at all over
 * the text being read.
 *
 * The system bars come and go with this row (see `ReaderWindow`), which is why the top padding is
 * measured against where the status bar sits rather than whether it is showing: hidden bars report a
 * zero inset, and reading the live value would start the row flush against the bezel and slide it
 * down as the bar animates in behind it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderChrome(
    visible: Boolean,
    isPlaying: Boolean,
    menuOpen: Boolean,
    hasContents: Boolean,
    canSearch: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSearch: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
    onBrightness: () -> Unit,
    onBookmark: () -> Unit,
    onChange: () -> Unit,
    onUnlink: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // Fills the screen, not just its content: the row aligns to the top edge, and a box sized to
        // its child would leave the scrim behind it nothing to stretch across.
        Box(modifier = Modifier.fillMaxSize()) {
            // The controls sit over live prose, and without this they collide with it. The Player's
            // cover solves the same problem the same way, and for the same reason: one treatment
            // that is safe over any content.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(SCRIM_HEIGHT)
                    .background(Brush.verticalGradient(listOf(ReaderScrim, Color.Transparent))),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTooltip(stringResource(R.string.reader_back)) {
                    ChromeButton(onClick = onBack) {
                        ChevronIcon(
                            direction = HorizontalDirection.Left,
                            size = 24.dp,
                            color = ReaderChromeInk,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val playPauseLabel = stringResource(
                        if (isPlaying) R.string.player_pause else R.string.player_play,
                    )
                    IconTooltip(playPauseLabel) {
                        ChromeButton(onClick = onPlayPause) {
                            if (isPlaying) {
                                PauseIcon(20.dp, ReaderChromeInk, playPauseLabel)
                            } else {
                                PlayIcon(20.dp, ReaderChromeInk, playPauseLabel)
                            }
                        }
                    }
                    // The menu is anchored inside this box rather than to the row: a `DropdownMenu`
                    // positions itself against its parent, and the row's parent is the whole screen.
                    Box {
                        val moreLabel = stringResource(R.string.reader_more)
                        IconTooltip(moreLabel) {
                            ChromeButton(onClick = { onMenuOpenChange(true) }) {
                                OverflowIcon(20.dp, ReaderChromeInk, moreLabel)
                            }
                        }
                        ReaderMenu(
                            expanded = menuOpen,
                            hasContents = hasContents,
                            canSearch = canSearch,
                            onDismiss = { onMenuOpenChange(false) },
                            onSearch = onSearch,
                            onContents = onContents,
                            onSettings = onSettings,
                            onBrightness = onBrightness,
                            onBookmark = onBookmark,
                            onChange = onChange,
                            onUnlink = onUnlink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Everything the reader does that is not turning pages or starting the audio.
 *
 * Words rather than icons, and no icons beside the words either. Half of these — changing the linked
 * ebook, unlinking it, brightness — have no glyph that says what they do without being learned
 * first, and a menu where three rows carry a picture and three do not reads as unfinished. A menu is
 * the one place in this app where a label is free.
 *
 * The two ebook-management actions sit below a divider because they are a different kind of thing:
 * the rest change how this book is being read, those two change which book is linked at all.
 */
@Composable
private fun ReaderMenu(
    expanded: Boolean,
    hasContents: Boolean,
    canSearch: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
    onBrightness: () -> Unit,
    onBookmark: () -> Unit,
    onChange: () -> Unit,
    onUnlink: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = ReaderSheet,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (hasContents) {
            ReaderMenuItem(stringResource(R.string.reader_contents)) { onDismiss(); onContents() }
        }
        if (canSearch) {
            ReaderMenuItem(stringResource(R.string.reader_search)) { onDismiss(); onSearch() }
        }
        ReaderMenuItem(stringResource(R.string.reader_bookmark)) { onDismiss(); onBookmark() }
        ReaderMenuItem(stringResource(R.string.reader_settings)) { onDismiss(); onSettings() }
        ReaderMenuItem(stringResource(R.string.reader_brightness)) { onDismiss(); onBrightness() }

        HorizontalDivider(
            color = ReaderChromeFill,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        ReaderMenuItem(stringResource(R.string.reader_change)) { onDismiss(); onChange() }
        ReaderMenuItem(stringResource(R.string.reader_unlink)) { onDismiss(); onUnlink() }
    }
}

@Composable
private fun ReaderMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 15.sp) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = ReaderChromeInk),
    )
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
 *
 * Opens on the chapter being read, centered and bold, rather than at the top of the book. A
 * ninety-entry contents list that always starts at entry one asks the reader to scroll to find
 * where they already are, every time they open it, and the answer is the one thing the sheet
 * already knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    entries: List<NavEntry>,
    currentBlockIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (NavEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(entries, currentBlockIndex) { entries.entryIndexAt(currentBlockIndex) }

    // Keyed on `Unit`, so it runs once when the sheet opens: keying it on the position would
    // re-scroll the list under the reader's finger as the narration advances.
    LaunchedEffect(Unit) {
        val index = currentIndex ?: return@LaunchedEffect
        listState.scrollToItem(index)
        // That puts the entry against the top edge. Centering it needs the measured row height and
        // viewport, which exist only once the list has laid out at the new position.
        withFrameNanos { }
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        // Negative offset lowers the row. Near either end of the book the list simply runs out of
        // travel and stops short, which is the right answer — there is nothing to center against.
        listState.scrollToItem(index, -(viewport - row.size) / 2)
    }

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

        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp)) {
            itemsIndexed(entries) { index, entry ->
                val isCurrent = index == currentIndex
                Text(
                    text = entry.label,
                    // A nested entry is dimmed to keep it under its part, but not while it is the
                    // one being read — dim and bold at once reads as a rendering mistake.
                    color = if (isCurrent || entry.depth == 0) ReaderChromeInk else ReaderChromeInkDim,
                    fontSize = if (entry.depth == 0) 16.sp else 15.sp,
                    fontWeight = when {
                        isCurrent -> FontWeight.Bold
                        entry.depth == 0 -> FontWeight.Medium
                        else -> FontWeight.Normal
                    },
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
 * Which entry [blockIndex] falls inside: the one starting nearest at or before it, or `null` when
 * the reader is in front matter that precedes every entry.
 *
 * Nearest rather than last-in-order, because a nav document's play order need not agree with spine
 * order (spike finding 6) and the list is therefore not guaranteed to climb. Ties go to the later
 * entry, which is the deeper one where a part and its first chapter start on the same block.
 */
private fun List<NavEntry>.entryIndexAt(blockIndex: Int): Int? =
    withIndex()
        .filter { it.value.blockIndex <= blockIndex }
        .maxWithOrNull(compareBy({ it.value.blockIndex }, { it.index }))
        ?.index

/**
 * Searching the ebook's text.
 *
 * The same sheet as the table of contents, and for the same reason: both are lists of places in the
 * book, and a hit behaves exactly like a contents entry once selected. The field takes focus on
 * open, because a search sheet that needs a second tap before it can be typed into is a sheet that
 * costs two taps every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    query: String,
    hits: List<SearchHit>,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (SearchHit) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ReaderSheet,
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.reader_search_hint),
                    color = ReaderChromeInkDim,
                    fontSize = 15.sp,
                )
            },
            leadingIcon = { SearchIcon(20.dp, ReaderChromeInkDim, null) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = ReaderChromeInk,
                unfocusedTextColor = ReaderChromeInk,
                focusedContainerColor = ReaderChromeFill,
                unfocusedContainerColor = ReaderChromeFill,
                cursorColor = ReaderChromeInk,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .focusRequester(focusRequester),
        )

        val tooShort = query.trim().length < Ebook.MIN_SEARCH_LENGTH
        if (hits.isEmpty()) {
            Text(
                text = stringResource(
                    if (tooShort) R.string.reader_search_prompt else R.string.reader_search_empty,
                ),
                color = ReaderChromeInkDim,
                fontSize = 15.sp,
                modifier = Modifier.padding(24.dp),
            )
            return@ModalBottomSheet
        }

        LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
            items(hits) { hit ->
                Text(
                    text = highlight(hit),
                    color = ReaderChromeInkDim,
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(hit) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The matched words in full white against the snippet's dimmer surroundings. */
private fun highlight(hit: SearchHit) = buildAnnotatedString {
    append(hit.snippet)
    addStyle(
        SpanStyle(color = ReaderChromeInk, fontWeight = FontWeight.Medium),
        hit.matchStart,
        hit.matchEnd,
    )
}

/**
 * Text size, line spacing, and typeface.
 *
 * Steppers rather than sliders: each of these has a small number of useful values, and a stepper
 * can be hit without looking, which is the same argument the Player's speed chips answer to.
 *
 * Brightness used to be the fourth row here and is now [BrightnessSheet]. It was the odd one out —
 * the other three change how the book is set, brightness changes the room — and burying it under a
 * button labeled `Aa` meant the one setting a reader reaches for in the dark was the one hardest to
 * find.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    settings: ReadingSettings,
    onDismiss: () -> Unit,
    onTextScale: (Float) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onSerif: (Boolean) -> Unit,
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

/**
 * Brightness, on its own, one step from the menu.
 *
 * The same stepper it always was and the same sheet the others use — what changed is that it is no
 * longer three taps deep behind a typography control it has nothing to do with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessSheet(
    settings: ReadingSettings,
    onDismiss: () -> Unit,
    onBrightness: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ReaderSheet,
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
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
