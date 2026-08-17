package com.brandonmiller.audiobookplayer.ui.theme

import androidx.compose.ui.graphics.Color

// A warm neutral palette, picked for text contrast rather than decoration (PRD §21).
// These are fixed values on purpose: the app does not use wallpaper-derived dynamic
// color, so contrast stays predictable for large controls used while walking (design D6
// of `bootstrap-android-project`).
//
// The `redesign-player-and-library` values below extend that palette rather than replacing it.
// Ink is a slightly cooler near-black than the original onBackground, and the surface is whiter
// than the surface it replaced, because the cover art now dominates the Player and the
// surround should recede behind it.

// --- Light ---

val Surface = Color(0xFFFFFDFB)
val Ink = Color(0xFF121110)
val OnInk = Color(0xFFFFFDFB)
val InkMuted = Color(0xFF3F3A35)
val TextSecondary = Color(0xFF57514B)
val TextTertiary = Color(0xFF6B645D)
val TextQuaternary = Color(0xFF9A9189)
val Track = Color(0xFFEBE3DB)
val Fill = Color(0xFFF0E9E2)
val ErrorTint = Color(0xFFF0E4E2)

/** The duration on an inverted chapter row: legible on [Ink] without competing with the title. */
val OnInkMuted = Color(0xFFCFC7BF)

/** The same role in dark, where the inverted row is light and its duration has to darken instead. */
val OnInkMutedDark = Color(0xFF4A4640)

val LightPrimaryContainer = Color(0xFFD6E0EC)
val LightOnPrimaryContainer = Color(0xFF16202B)
val LightSecondary = Color(0xFF5A5248)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE6DED3)
val LightOnSecondaryContainer = Color(0xFF1D1913)
val LightOutline = Color(0xFF7C7770)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

// --- Dark ---
//
// The same roles inverted, with one rule holding it together: the play button, the scrubber fill,
// the current-chapter row, and the empty state's primary button all flip to light-on-dark, so the
// brightest thing on screen is still the thing being pressed.

val SurfaceDark = Color(0xFF131211)

/** Bottom sheets, one step lighter than the screen, which is what makes them read as lifted. */
val SurfaceRaisedDark = Color(0xFF1C1A18)
val InkDark = Color(0xFFF7F3EF)
val OnInkDark = Color(0xFF131211)
val InkMutedDark = Color(0xFFC6BEB6)
val TextSecondaryDark = Color(0xFFA9A19A)
val TextTertiaryDark = Color(0xFF8A827A)
val TextQuaternaryDark = Color(0xFF6E6660)
val TrackDark = Color(0xFF2E2A27)
val FillDark = Color(0xFF221F1D)

/** The "Keep it" button on the removal sheet, which sits on [SurfaceRaisedDark] rather than the screen. */
val FillPressedDark = Color(0xFF2A2724)
val ErrorTintDark = Color(0xFF2B1A18)

val DarkPrimaryContainer = Color(0xFF2A3B4C)
val DarkOnPrimaryContainer = Color(0xFFD6E0EC)
val DarkSecondary = Color(0xFFCBC0B2)
val DarkOnSecondary = Color(0xFF322C24)
val DarkSecondaryContainer = Color(0xFF493F35)
val DarkOnSecondaryContainer = Color(0xFFE6DED3)
val DarkOutline = Color(0xFF968E86)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** Coverless-book thumbs. Two of them, alternated by book id, so a shelf of them is not one flat block. */
val CoverPlaceholder1 = LightSecondaryContainer
val CoverPlaceholder2 = LightPrimaryContainer
val CoverPlaceholder1Dark = Color(0xFF3A332B)
val CoverPlaceholder2Dark = Color(0xFF2C3742)

/** Behind the removal sheet. Deeper in dark, where a 50% scrim would not separate the two layers. */
val ScrimLight = Color(0x80121110)
val ScrimDark = Color(0x9E060605)

/** The Player cover's top scrim, so the status bar and back button stay legible on any artwork. */
val CoverScrimLight = Color(0x73121110)
val CoverScrimDark = Color(0x800B0A09)

/** The back button's own disc, which has to hold up over a bright cover as well as a dark one. */
val BackScrimLight = Color(0x6B121110)
val BackScrimDark = Color(0x800B0A09)
