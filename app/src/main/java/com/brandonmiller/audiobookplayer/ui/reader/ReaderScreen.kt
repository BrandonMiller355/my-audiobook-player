package com.brandonmiller.audiobookplayer.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.SnackbarResult
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Block
import com.brandonmiller.audiobookplayer.ebook.BlockKind
import com.brandonmiller.audiobookplayer.ebook.Emphasis
import com.brandonmiller.audiobookplayer.ebook.TextPosition
import com.brandonmiller.audiobookplayer.ui.library.OpenPersistableDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.absoluteValue

/**
 * Pure black with white text, in both themes.
 *
 * Reader-local rather than roles on `AudiobookColors` (`add-ebook-companion` design D6). That
 * palette's stated contract is that light and dark differ only in color while the layout stays
 * identical; a screen that is the same in both is outside it, and threading these through would
 * make the palette describe something it does not govern. Note this is blacker than the app's own
 * dark surface, which is a warm `0xFF131211` — the difference is the point on an OLED panel.
 */
private val ReaderBackground = Color(0xFF000000)
private val ReaderInk = Color(0xFFFFFFFF)
private val ReaderInkDim = Color(0xFFB4B4B4)
private val ReaderRule = Color(0xFF3A3A3A)

/**
 * Long enough to read a row of six controls and choose one. Four seconds proved too short in device
 * testing — the chrome kept vanishing between deciding and reaching.
 */
private const val AUTO_HIDE_MS = 6_000L

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(LocalContext.current, bookId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Visible on entry so the reveal gesture is discoverable — otherwise a user who does not know
    // to tap has a black page and no way off it.
    var chromeVisible by remember { mutableStateOf(true) }
    var revealCount by remember { mutableStateOf(0) }
    var restored by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var contentsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    // The loop guard (design D4). Set only by a scroll the user's finger caused, so the reader
    // following the narration never looks like the user moving the text. A flick reports
    // `UserInput` for the drag and `SideEffect` for the fling after it, so setting on `UserInput`
    // and clearing after settle covers the whole gesture; a programmatic scroll reports only
    // `SideEffect` and so never sets it.
    var userScrolled by remember { mutableStateOf(false) }
    val userInputGate = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) userScrolled = true
                return Offset.Zero
            }
        }
    }

    val readAlongActive = state.readAlongMap != null

    val pickEbook = rememberLauncherForActivityResult(OpenPersistableDocument()) { uri ->
        uri?.let(viewModel::changeEbook)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pickError = state.pickErrorMessage?.let { stringResource(it) }

    ReaderWindow(state.settings)

    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    LaunchedEffect(pickError) {
        pickError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumePickError()
        }
    }

    // Restore where the user was reading, then let scrolling start saving. Ordered on purpose:
    // saving before the restore has happened would write position zero over the saved one.
    LaunchedEffect(state.scrollToBlock) {
        state.scrollToBlock?.let { target ->
            listState.scrollToItem(target)
            viewModel.consumeScrollTarget()
            restored = true
        }
    }

    // Settle. Gated on the flag rather than on `isScrollInProgress` alone, because the reader
    // scrolls itself constantly while following the narration and every one of those steps also
    // reports a settle (design D4). Without the gate this both seeks the audio backward forever and
    // writes the reading position once a frame.
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect {
                if (!userScrolled) return@collect
                userScrolled = false

                val anchor = listState.textPositionAtAnchor() ?: return@collect
                if (readAlongActive) {
                    viewModel.seekToTextPosition(
                        blockIndex = anchor.blockIndex,
                        fraction = anchor.fraction,
                        previousPositionMs = viewModel.currentPositionMs() ?: 0L,
                    )
                }
                viewModel.saveReadingPosition(anchor.blockIndex)
            }
    }

    // The glide (design D7). Runs per frame rather than per position sample: the target has to
    // advance by a fraction of a pixel at a time for the page to read as moving rather than
    // stepping, and a 250ms sample cannot express that. `currentTextPosition` extrapolates from the
    // controller, so the value it returns is genuinely continuous between samples.
    LaunchedEffect(readAlongActive, state.book) {
        if (!readAlongActive) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            // Yield the whole gesture to the user, drag and fling alike, rather than fighting it.
            if (listState.isScrollInProgress && userScrolled) continue

            val target = viewModel.currentTextPosition() ?: continue
            val info = listState.layoutInfo
            val onScreen = info.visibleItemsInfo.firstOrNull { it.index == target.blockIndex }

            if (onScreen == null) {
                // Off screen — a seek, a chapter skip, or opening the reader. Nothing to glide
                // toward, because `layoutInfo` cannot measure what it has not laid out.
                listState.scrollToItem(target.blockIndex)
                continue
            }

            val targetPx = onScreen.offset + onScreen.size * target.fraction
            val delta = targetPx - info.anchorPx()
            // Sub-pixel deltas are the normal case at reading speed; LazyList accumulates them, so
            // they must not be rounded away. The threshold only suppresses idle jitter while paused.
            if (delta.absoluteValue > 0.01f) listState.scrollBy(delta)
        }
    }

    LaunchedEffect(chromeVisible, revealCount) {
        if (!chromeVisible) return@LaunchedEffect
        delay(AUTO_HIDE_MS)
        chromeVisible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReaderBackground)
            .pointerInput(Unit) {
                // A scroll consumes its own events, so a drag never reaches this as a tap.
                detectTapGestures {
                    chromeVisible = !chromeVisible
                    revealCount++
                }
            },
    ) {
        when {
            state.loading -> ReaderMessage(stringResource(R.string.reader_loading))

            state.unavailableMessage != null -> ReaderUnavailable(
                message = stringResource(state.unavailableMessage!!),
                onRelink = { pickEbook.launch(OpenPersistableDocument.EBOOK_MIME_TYPES) },
                onBack = onBack,
            )

            else -> state.book?.let { book ->
                ReaderText(
                    blocks = book.blocks,
                    settings = state.settings,
                    listState = listState,
                    modifier = Modifier.nestedScroll(userInputGate),
                )
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            isPlaying = state.isPlaying,
            hasContents = state.book?.contents?.isNotEmpty() == true,
            canSearch = state.book != null,
            onBack = onBack,
            onPlayPause = viewModel::togglePlayPause,
            onSearch = { searchOpen = true },
            onContents = { contentsOpen = true },
            onSettings = { settingsOpen = true },
            onChange = { pickEbook.launch(OpenPersistableDocument.EBOOK_MIME_TYPES) },
            onUnlink = viewModel::unlinkEbook,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (contentsOpen) {
        ContentsSheet(
            entries = state.book?.contents.orEmpty(),
            onDismiss = { contentsOpen = false },
            onSelect = { entry ->
                viewModel.jumpToBlock(entry.blockIndex)
                contentsOpen = false
            },
        )
    }

    // The query and its hits outlive the sheet on purpose: reopening search after following one hit
    // is how a reader checks the next one, and retyping the word to do it would be a chore.
    if (searchOpen) {
        SearchSheet(
            query = state.searchQuery,
            hits = state.searchHits,
            onQuery = viewModel::search,
            onDismiss = { searchOpen = false },
            onSelect = { hit ->
                viewModel.jumpToBlock(hit.blockIndex)
                searchOpen = false
            },
        )
    }

    if (settingsOpen) {
        ReadingSettingsSheet(
            settings = state.settings,
            onDismiss = { settingsOpen = false },
            onTextScale = viewModel::setTextScale,
            onLineSpacing = viewModel::setLineSpacing,
            onSerif = viewModel::setSerif,
            onBrightness = viewModel::setBrightness,
        )
    }
}

/**
 * Where on screen the narrated word is held.
 *
 * A third of the way down rather than at the top edge: reading happens a little below the top, and
 * an anchor at zero would keep the current sentence flush against the bezel with the whole viewport
 * of already-read text below it.
 */
private const val ANCHOR_FRACTION = 0.33f

/** The anchor line in the same coordinate space `LazyListItemInfo.offset` is measured in. */
private fun LazyListLayoutInfo.anchorPx(): Float =
    viewportStartOffset + (viewportEndOffset - viewportStartOffset) * ANCHOR_FRACTION

/**
 * What the reader is showing at the anchor, to sub-block precision (design D6).
 *
 * The reverse of the glide: it reads the rendered heights back out of `layoutInfo` to say how far
 * into the anchored block the anchor line falls. `firstVisibleItemIndex` is what this replaces, and
 * the difference matters because a settle now seeks — a 400-word paragraph rounded to its start is
 * over thirty seconds of narration thrown away.
 */
private fun LazyListState.textPositionAtAnchor(): TextPosition? {
    val info = layoutInfo
    val anchor = info.anchorPx()
    val item = info.visibleItemsInfo.lastOrNull { it.offset <= anchor }
        ?: info.visibleItemsInfo.firstOrNull()
        ?: return null
    val fraction = if (item.size <= 0) 0f else ((anchor - item.offset) / item.size).coerceIn(0f, 1f)
    return TextPosition(item.index, fraction)
}

/**
 * The window properties reading needs: the screen stays on, the brightness is the user's, and the
 * system bars match the page. All three are restored on the way out — the Player's own
 * `LightStatusBarIcons` takes the same shape and for the same reason.
 */
@Composable
private fun ReaderWindow(settings: ReadingSettings) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return

    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNav
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // A window attribute, not a system setting, so it affects this app only and needs no
    // permission. Restored to BRIGHTNESS_OVERRIDE_NONE so the rest of the app is unaffected.
    DisposableEffect(settings.brightness) {
        val attributes = window.attributes
        attributes.screenBrightness = if (settings.followsSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.brightness
        }
        window.attributes = attributes

        onDispose {
            val restored = window.attributes
            restored.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = restored
        }
    }
}

/**
 * The whole book in one list.
 *
 * Every block is a paragraph rather than a document, so a book that ships as one enormous XHTML
 * file is as lazy as any other, and each `AnnotatedString` stays small (design D11, R3).
 */
@Composable
private fun ReaderText(
    blocks: List<Block>,
    settings: ReadingSettings,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(count = blocks.size, key = { it }) { index ->
            ReaderBlock(blocks[index], settings)
        }
    }
}

@Composable
private fun ReaderBlock(block: Block, settings: ReadingSettings) {
    if (block.kind == BlockKind.Rule) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                .height(1.dp)
                .background(ReaderRule),
        )
        return
    }

    val text = remember(block) { annotate(block) }
    val style = blockStyle(block, settings)

    Text(
        text = text,
        style = style,
        color = if (block.kind == BlockKind.Quote) ReaderInkDim else ReaderInk,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (block.kind == BlockKind.Quote || block.kind == BlockKind.ListItem) 18.dp else 0.dp,
                top = if (block.kind == BlockKind.Heading) 28.dp else 0.dp,
                bottom = if (block.kind == BlockKind.Heading) 10.dp else 12.dp,
            ),
    )
}

/** Applies the parser's emphasis runs, plus a list item's own marker. */
private fun annotate(block: Block) = buildAnnotatedString {
    if (block.kind == BlockKind.ListItem) {
        append(block.listOrdinal?.let { "$it. " } ?: "• ")
    }
    val offset = length
    append(block.text)

    block.emphasis.forEach { span ->
        addStyle(
            SpanStyle(
                fontStyle = if (Emphasis.Italic in span.styles) FontStyle.Italic else null,
                fontWeight = if (Emphasis.Bold in span.styles) FontWeight.Bold else null,
                textDecoration = if (Emphasis.Underline in span.styles) TextDecoration.Underline else null,
            ),
            offset + span.start,
            offset + span.end,
        )
    }
}

/**
 * Body size scales with the user's setting and line height with it, so changing one does not
 * silently undo the other. Headings step up from the same base for the same reason.
 */
private fun blockStyle(block: Block, settings: ReadingSettings): TextStyle {
    val family = if (settings.serif) FontFamily.Serif else FontFamily.SansSerif
    val bodySp = BASE_BODY_SP * settings.textScale

    val sizeSp = when {
        block.kind != BlockKind.Heading -> bodySp
        block.headingLevel <= 1 -> bodySp * 1.6f
        block.headingLevel == 2 -> bodySp * 1.35f
        else -> bodySp * 1.15f
    }

    return TextStyle(
        fontFamily = family,
        fontSize = sizeSp.sp,
        lineHeight = settings.lineSpacing.em,
        fontWeight = if (block.kind == BlockKind.Heading) FontWeight.SemiBold else FontWeight.Normal,
        fontStyle = if (block.kind == BlockKind.Quote) FontStyle.Italic else FontStyle.Normal,
    )
}

private const val BASE_BODY_SP = 18f

@Composable
private fun ReaderMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = ReaderInkDim, fontSize = 16.sp)
    }
}

@Composable
private fun ReaderUnavailable(message: String, onRelink: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_unavailable_title),
                color = ReaderInk,
                fontSize = 20.sp,
            )
            Text(text = message, color = ReaderInkDim, fontSize = 15.sp)
            ReaderTextButton(stringResource(R.string.reader_unavailable_relink), onRelink)
            ReaderTextButton(stringResource(R.string.reader_back), onBack)
        }
    }
}
