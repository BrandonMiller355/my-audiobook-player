package com.brandonmiller.audiobookplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The redesign's colors that have no Material role to live in.
 *
 * Material 3 offers two levels of text emphasis, `onSurface` and `onSurfaceVariant`; this design
 * uses four, plus a set of fills, scrims, and tints that answer to nothing in the Material palette.
 * Stretching those over unrelated roles — `tertiary` standing in for "mono metadata", `outline`
 * for "uppercase label" — would make every call site misleading about what the color is for.
 *
 * Holding them here instead keeps the theme the single place light and dark diverge, which is what
 * makes the handoff's promise — layout identical between the two, only the palette changing —
 * something that can be checked rather than hoped for (design D1).
 */
@Immutable
data class AudiobookColors(
    /** The screen's background. */
    val surface: Color,
    /** Bottom sheets, which in dark sit one step lighter than the screen. */
    val surfaceRaised: Color,
    /** Primary text, and the fill behind every primary control. */
    val ink: Color,
    /** Text and icons drawn on [ink]. */
    val onInk: Color,
    /** A chapter duration on an inverted row — present on [ink] without competing with the title. */
    val onInkMuted: Color,
    /** Secondary icons: the ±1m seek pair, and chapter titles in the sheet. */
    val inkMuted: Color,
    /** Body copy and supporting text. */
    val textSecondary: Color,
    /** Mono metadata: durations, chapter counts. */
    val textTertiary: Color,
    /** Uppercase labels, chapter numbers, captions, hints. */
    val textQuaternary: Color,
    /** Progress and scrubber tracks, and every hairline. */
    val track: Color,
    /** Neutral fills: unselected chips, secondary buttons, the add circle. */
    val fill: Color,
    /** A fill that has to separate from [surfaceRaised] rather than from [surface]. */
    val fillOnRaised: Color,
    /** Unavailable-source text and its warning icon. */
    val error: Color,
    /** The thumb background for a book whose source is gone. */
    val errorTint: Color,
    /** The destructive button's fill. */
    val errorFill: Color,
    /** Text on [errorFill]. */
    val onErrorFill: Color,
    /** Coverless-book thumbs, alternated so a shelf of them is not one flat block. */
    val coverPlaceholder1: Color,
    val coverPlaceholder2: Color,
    /** Behind the removal sheet. */
    val scrim: Color,
    /** Over the top of the Player's cover, under the status bar. */
    val coverScrim: Color,
    /** The back button's disc on the Player. */
    val backScrim: Color,
    /**
     * Dark only: a fade from transparent to the screen color over the cover's bottom 64dp. In
     * light there is none — in dark, without it, a bright cover's bottom edge reads as a seam
     * against the surround rather than as the end of the artwork.
     */
    val coverBaseFade: Color?,
)

private val LightAudiobookColors = AudiobookColors(
    surface = Surface,
    surfaceRaised = Surface,
    ink = Ink,
    onInk = OnInk,
    onInkMuted = OnInkMuted,
    inkMuted = InkMuted,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    textQuaternary = TextQuaternary,
    track = Track,
    fill = Fill,
    fillOnRaised = Fill,
    error = LightError,
    errorTint = ErrorTint,
    errorFill = LightError,
    onErrorFill = OnInk,
    coverPlaceholder1 = CoverPlaceholder1,
    coverPlaceholder2 = CoverPlaceholder2,
    scrim = ScrimLight,
    coverScrim = CoverScrimLight,
    backScrim = BackScrimLight,
    coverBaseFade = null,
)

private val DarkAudiobookColors = AudiobookColors(
    surface = SurfaceDark,
    surfaceRaised = SurfaceRaisedDark,
    ink = InkDark,
    onInk = OnInkDark,
    onInkMuted = OnInkMutedDark,
    inkMuted = InkMutedDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    textQuaternary = TextQuaternaryDark,
    track = TrackDark,
    fill = FillDark,
    fillOnRaised = FillPressedDark,
    error = DarkError,
    errorTint = ErrorTintDark,
    errorFill = DarkErrorContainer,
    onErrorFill = DarkOnErrorContainer,
    coverPlaceholder1 = CoverPlaceholder1Dark,
    coverPlaceholder2 = CoverPlaceholder2Dark,
    scrim = ScrimDark,
    coverScrim = CoverScrimDark,
    backScrim = BackScrimDark,
    coverBaseFade = SurfaceDark,
)

/**
 * No sensible default: a composable reading this outside [AudiobooksTheme] would silently get the
 * light palette in a dark app, which is exactly the bug this indirection exists to prevent.
 */
private val LocalAudiobookColors = staticCompositionLocalOf<AudiobookColors> {
    error("AudiobookColors used outside AudiobooksTheme")
}

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = OnInk,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Fill,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    outline = LightOutline,
    outlineVariant = Track,
    error = LightError,
    onError = LightOnError,
    scrim = ScrimLight,
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = OnInkDark,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = SurfaceDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SurfaceRaisedDark,
    surfaceContainer = SurfaceRaisedDark,
    surfaceContainerHigh = SurfaceRaisedDark,
    outline = DarkOutline,
    outlineVariant = TrackDark,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = ScrimDark,
)

/**
 * Follows the system light/dark setting. Dynamic (wallpaper-derived) color is
 * deliberately not offered — see `bootstrap-android-project` design decision D6.
 */
@Composable
fun AudiobooksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAudiobookColors provides if (darkTheme) DarkAudiobookColors else LightAudiobookColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AudiobooksTypography,
            content = content,
        )
    }
}

/** The redesign's palette, alongside `MaterialTheme.colorScheme` rather than instead of it. */
val audiobookColors: AudiobookColors
    @Composable @ReadOnlyComposable get() = LocalAudiobookColors.current
