package com.brandonmiller.audiobookplayer.ui.reader

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import kotlin.math.roundToInt

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
 * The selection wash and its handles, reader-local for the reason the colors above are.
 *
 * Compose derives selection colors from the Material theme's primary, and this app's primary is a
 * muted warm neutral — which on a pure black page washes out to very nearly black. On device the
 * highlight was almost impossible to see. Amber is a highlighter, it is unmistakable against black,
 * and it sits with the palette's warmth rather than fighting it.
 *
 * The alpha is the whole balance: the band has to read as marked against pure black while the white
 * text drawn over it stays comfortable. At this value the wash composites to roughly `0xFF685121`,
 * which clears the page by a visible margin and still leaves the text far above the contrast floor.
 */
private val ReaderSelectionHandle = Color(0xFFE8B44A)
private val ReaderSelectionWash = ReaderSelectionHandle.copy(alpha = 0.45f)

/**
 * Long enough to read the controls and choose one. Four seconds proved too short in device testing —
 * the chrome kept vanishing between deciding and reaching. The row is shorter now that the rest of
 * the actions are behind a menu, but the reach is the same and the menu itself holds this open.
 */
private const val AUTO_HIDE_MS = 6_000L

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenNotes: (Long) -> Unit,
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
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var brightnessOpen by remember { mutableStateOf(false) }
    var syncOpen by remember { mutableStateOf(false) }
    var contentsOpen by remember { mutableStateOf(false) }
    // Sampled when the sheet opens rather than read during composition: `layoutInfo` is snapshot
    // state, and observing it here would recompose the whole reader on every frame of every scroll.
    var contentsAnchorBlock by remember { mutableStateOf(0) }
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

    ReaderWindow(state.settings, chromeVisible)

    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    LaunchedEffect(pickError) {
        pickError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumePickError()
        }
    }

    // Says that Copy worked, because nothing else does.
    //
    // Copy belongs to the platform's own selection toolbar, and the platform normally answers it with
    // the clipboard chip SystemUI puts up. It does not here: this screen runs with the system bars
    // hidden, and SystemUI suppresses that overlay — device logs say so in as many words. The copy
    // lands, silently, which is indistinguishable from a dead button.
    //
    // The clip changing is the only hook there is. The toolbar item is the platform's, and Compose
    // exposes neither the live selection nor the action, so there is nothing of ours to hang this on.
    // Listening costs no permission and reads nothing: the callback carries no clip, only the fact
    // that one arrived, which is all this needs to say "Copied".
    val context = LocalContext.current
    var copyCount by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener { copyCount++ }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }

    val copiedMessage = stringResource(R.string.reader_selection_copied)
    LaunchedEffect(copyCount) {
        if (copyCount > 0) snackbarHostState.showSnackbar(copiedMessage)
    }

    // Same flow as the Player's mark: the navigation is the confirmation (design D8).
    LaunchedEffect(state.markTaken) {
        state.markTaken?.let { noteId ->
            viewModel.consumeMarkTaken()
            onOpenNotes(noteId)
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
    // Gated on `restored` as well: the restore below is also a `scrollToItem`, and two of them
    // aiming at different blocks on the reader's first frames is a visible fight the user watches.
    // The audio's place in the text wins in the end either way, so it costs nothing to wait.
    LaunchedEffect(readAlongActive, state.book, restored) {
        if (!readAlongActive || !restored) return@LaunchedEffect
        var settled = false
        while (true) {
            // Per frame only while there is something to chase.
            //
            // This used to pump `withFrameNanos` unconditionally, forever, and that was a real bug
            // rather than a tidiness problem. An awaited frame schedules the next one, so a paused
            // book — whose target cannot move on its own — still rendered and relaid the list out
            // continuously over a page that was not moving. Measured on device: 118 frames in four
            // idle seconds, most of them janky, on a screen that also holds the display awake.
            //
            // What it broke is worse than the waste. A relayout while text is selected moves the
            // platform's selection toolbar, and a toolbar that moves between a finger going down and
            // coming up does not register the press — so Copy silently did nothing, often enough to
            // look broken and intermittently enough to look haunted.
            //
            // Paused and converged, the target only moves if the owner nudges or seeks, and a
            // quarter second is soon enough to catch either: the glide resumes per frame the moment
            // a delta appears, so a nudge is still carried smoothly rather than stepped.
            if (settled && !state.isPlaying) delay(GLIDE_IDLE_POLL_MS) else withFrameNanos { }
            // Yield the whole gesture to the user, drag and fling alike, rather than fighting it.
            if (listState.isScrollInProgress && userScrolled) continue

            // Nothing to follow yet — treat it as arrived rather than spinning on it.
            val target = viewModel.currentTextPosition() ?: run { settled = true; continue }
            val info = listState.layoutInfo
            val anchorPx = info.anchorPx()
            val onScreen = info.visibleItemsInfo.firstOrNull { it.index == target.blockIndex }

            if (onScreen == null) {
                // Off screen — a seek, a chapter skip, or opening the reader. Nothing to glide
                // toward, because `layoutInfo` cannot measure what it has not laid out.
                //
                // The offset lands the block on the anchor rather than at the top of the viewport.
                // `scrollToItem` alone leaves it a third of a screen too high, and the glide's very
                // next frame reads that as an error to close — so the recovery becomes a jump
                // forward followed by a jump back up, which is the opposite of following anything.
                listState.scrollToItem(target.blockIndex, -anchorPx.roundToInt())
                settled = false
                continue
            }

            val targetPx = onScreen.offset + onScreen.size * target.fraction
            val delta = targetPx - anchorPx
            // Sub-pixel *steps* are the normal case at reading speed and are still applied — the
            // list accumulates them, and rounding them away would put stepping back into the glide.
            // What must not be sub-pixel is the test for having arrived. It used to be a hundredth
            // of a pixel, which is below anything a screen can show, so on a paused page the glide
            // spent every frame pushing a residual it had no way to close: device logs showed the
            // same `delta=-0.23` frame after frame, then a flip to `+0.77` as a whole pixel finally
            // landed, and back again, forever.
            //
            // A pixel is the real floor. Inside it the page is as arrived as it can be, so the glide
            // stops pushing and stops asking for frames. Playing is unaffected: the target keeps
            // moving, which holds the steady-state lag near a couple of pixels, well outside this.
            val step = delta * GLIDE_GAIN
            if (delta.absoluteValue >= GLIDE_SETTLE_PX) {
                listState.scrollBy(step)
                settled = false
            } else {
                settled = true
            }
        }
    }

    // Held open while the menu is: the menu is a popup anchored to a button inside the chrome, and
    // fading the chrome out from under it would leave it floating over nothing.
    //
    // Held open while a sheet is, for the reason the menu is not the only thing riding on this flag
    // any more: the system bars follow it now, so a timer that fires under an open contents list
    // takes the navigation bar away from a reader who is still using it. The timer restarts when the
    // sheet closes, so the six seconds are counted from the last thing the reader actually did.
    val sheetOpen = contentsOpen || searchOpen || settingsOpen || brightnessOpen || syncOpen
    LaunchedEffect(chromeVisible, revealCount, menuOpen, sheetOpen) {
        if (!chromeVisible || menuOpen || sheetOpen) return@LaunchedEffect
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
                SelectableWhilePaused(selectable = !state.isPlaying) {
                    ReaderText(
                        blocks = book.blocks,
                        settings = state.settings,
                        listState = listState,
                        modifier = Modifier.nestedScroll(userInputGate),
                    )
                }
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            isPlaying = state.isPlaying,
            menuOpen = menuOpen,
            hasContents = state.book?.contents?.isNotEmpty() == true,
            canSearch = state.book != null,
            canSync = readAlongActive,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onPlayPause = viewModel::togglePlayPause,
            onSearch = { searchOpen = true },
            onContents = {
                contentsAnchorBlock = listState.textPositionAtAnchor()?.blockIndex ?: 0
                contentsOpen = true
            },
            onSettings = { settingsOpen = true },
            onBrightness = { brightnessOpen = true },
            onChange = { pickEbook.launch(OpenPersistableDocument.EBOOK_MIME_TYPES) },
            onBookmark = viewModel::bookmark,
            onSync = { syncOpen = true },
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
            currentBlockIndex = contentsAnchorBlock,
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
        )
    }

    // Judged against the page behind it, so it stays open while the owner watches the text move
    // (design D11). Nothing here pauses or seeks: the nudge replaces the map and the glide carries
    // the page, which is what lets this be a sheet rather than a mode.
    if (syncOpen) {
        SyncSheet(
            correctionDeltaMs = state.correctionDeltaMs,
            atLimit = state.correctionAtLimit,
            onDismiss = { syncOpen = false },
            onNudge = viewModel::nudge,
        )
    }

    if (brightnessOpen) {
        BrightnessSheet(
            settings = state.settings,
            onDismiss = { brightnessOpen = false },
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

/**
 * How much of the distance to the narrated word the glide closes each frame.
 *
 * Closing all of it — following the target exactly, which is what this did before — gives the glide
 * unity gain, and unity gain passes everything through: every wobble in the position the reader
 * follows arrives on screen at full size, including the backward ones. Closing a fifth of it per
 * frame averages that away while still settling a real correction inside a few frames.
 *
 * The cost is a constant lag behind the target, of roughly one frame's travel divided by this — a
 * handful of pixels at reading speed, which is well inside the slack the anchor already has.
 */
private const val GLIDE_GAIN = 0.2f

/**
 * How often the glide looks up from a page that has nothing to follow.
 *
 * Only reached while the book is paused and the page has arrived, when the single thing that can
 * move the target is the owner's own hand — a nudge or a seek. Short enough that neither feels
 * delayed, long enough that an idle reader stops rendering.
 */
private const val GLIDE_IDLE_POLL_MS = 250L

/**
 * How close counts as arrived, in pixels.
 *
 * One pixel, because that is the smallest move a screen can render. Anything finer is a distance the
 * glide can measure and can never show, which is exactly the state it used to chase forever.
 */
private const val GLIDE_SETTLE_PX = 1f

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
private fun ReaderWindow(settings: ReadingSettings, chromeVisible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return

    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNav = controller.isAppearanceLightNavigationBars
        val previousBarBehavior = controller.systemBarsBehavior
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        // A swipe from either edge brings the bars back for a moment without disturbing the page.
        // The alternative behavior makes that swipe permanent, which would leave the bars on with
        // the chrome off — the one combination the reveal gesture is supposed to rule out.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNav
            controller.systemBarsBehavior = previousBarBehavior
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // The clock and the navigation buttons are chrome too. Hiding the reader's own controls while
    // leaving the system's framing the page defeats the point of a black page: what is left is a
    // book quoted between two bars. They come and go with the tap that reveals the controls.
    DisposableEffect(chromeVisible) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
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
 * Selection, but only against a page that is holding still (design D1).
 *
 * The gate is structural rather than a flag a gesture handler consults, which is what makes it
 * total: when playback starts the container leaves the composition and takes any live selection with
 * it, so "starting playback clears the selection" costs no code at all. `listState` is hoisted above
 * this, so the reading position survives the swap; play/pause is the only thing that triggers it.
 *
 * It is not here to resolve a gesture collision — there isn't one. Compose settles scroll against
 * selection by the long-press threshold: a pointer that moves before it goes to the scrollable, one
 * that holds still past it starts a selection, and touch-and-drag therefore scrolls either way. What
 * the gate is for is motion. While the narration plays, the glide moves the list every frame, and a
 * selection anchored to sliding text leaves the handles chasing their own content.
 */
@Composable
private fun SelectableWhilePaused(selectable: Boolean, content: @Composable () -> Unit) {
    if (!selectable) {
        content()
        return
    }
    val colors = remember {
        TextSelectionColors(
            handleColor = ReaderSelectionHandle,
            backgroundColor = ReaderSelectionWash,
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors) {
        SelectionContainer(content = content)
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
