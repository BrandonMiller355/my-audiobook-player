package com.brandonmiller.audiobookplayer.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.brandonmiller.audiobookplayer.R
import com.brandonmiller.audiobookplayer.data.ReadingSettings
import com.brandonmiller.audiobookplayer.ebook.Block
import com.brandonmiller.audiobookplayer.ebook.BlockKind
import com.brandonmiller.audiobookplayer.ebook.Emphasis
import com.brandonmiller.audiobookplayer.ui.library.OpenPersistableDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

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

    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect { viewModel.saveReadingPosition(listState.firstVisibleItemIndex) }
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
                ReaderText(blocks = book.blocks, settings = state.settings, listState = listState)
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            isPlaying = state.isPlaying,
            hasContents = state.book?.contents?.isNotEmpty() == true,
            onBack = onBack,
            onPlayPause = viewModel::togglePlayPause,
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
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
