package com.brandonmiller.audiobookplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.brandonmiller.audiobookplayer.R

/**
 * Two bundled families, both OFL, both shipped as static weights rather than fetched at runtime —
 * the app has no network permission and would not gain one for a typeface.
 *
 * The split between them is functional, not decorative. [Archivo] carries the words; [JetBrainsMono]
 * carries every numeral that changes while playback runs — timestamps, durations, speed, chapter
 * counts. A proportional face reflows those horizontally as digit widths change, which at a
 * half-second refresh reads as the scrubber's times twitching. Monospaced digits hold still.
 */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/**
 * The redesign's type scale, named for what each style is for rather than by a size ladder, so a
 * call site says what it is showing instead of how big it is.
 *
 * Sizes, weights, and letter spacing are the handoff's final values. Tracking is expressed in `em`
 * because the design gives it that way and because it then scales with the font size rather than
 * needing a recomputed `sp` per style.
 */
object AudiobookType {

    /** "Library" — the screen title, which is list content here rather than an app bar. */
    val displayScreen = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.03).em,
    )

    /** The empty library's headline. */
    val displayEmpty = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.03).em,
    )

    /** The book title on the Player. */
    val titlePlayer = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.025).em,
    )

    /** The resume card's book title, and the removal sheet's question. */
    val titleResume = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 27.6.sp,
        letterSpacing = (-0.02).em,
    )

    /** A library row's title, and the chapter sheet's current row. */
    val titleRow = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = (-0.01).em,
    )

    /** The chapter sheet's header title — the one 19sp style that is bold rather than semibold. */
    val titleSheet = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.02).em,
    )

    /** A chapter row's title, and every filled button's label. */
    val bodyLarge = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    )

    /** The same size at semibold — what the sheet and empty-state buttons use. */
    val bodyLargeStrong = bodyLarge.copy(fontWeight = FontWeight.SemiBold)

    /** The empty state's body, and the Player's connecting line. */
    val bodyEmpty = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.5.sp,
    )

    /** The Player footer's "Chapters" label. */
    val labelAction = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    )

    /** The removal sheet's body. */
    val bodyDialog = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /**
     * "Source unavailable — tap to relink". The one metadata line that is not mono, because it is
     * a sentence rather than a figure and should not be read as one.
     */
    val labelUnavailable = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )

    /** "KEEP LISTENING". Uppercasing is the caller's, not the style's. */
    val labelCaps = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    )

    /** Scrubber timestamps, and the speed value in the footer. */
    val monoTime = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )

    /** The footer's speed reading, the one mono style the design sets at medium. */
    val monoSpeed = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
    )

    /** Row metadata (`18 ch · 8h 40m`) and chapter durations. */
    val monoMeta = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )

    /** The Player's chapter line. Uppercased by the caller. */
    val monoCaps = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.04.em,
    )

    /** Seek captions, footer sub-labels, and the privacy note. */
    val monoMicro = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.04.em,
    )

    /** The chapter sheet's speed chips, and its chapter numbers at 14sp. */
    val monoChip = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )

    val monoNumber = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        textAlign = TextAlign.End,
    )
}

/**
 * Material's own slots, kept populated so that any component the app has not restyled by hand —
 * a snackbar, a dropdown item — still lands in Archivo rather than reverting to the platform face
 * beside everything that does not.
 */
val AudiobooksTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Archivo),
        displayMedium = displayMedium.copy(fontFamily = Archivo),
        displaySmall = displaySmall.copy(fontFamily = Archivo),
        headlineLarge = headlineLarge.copy(fontFamily = Archivo),
        headlineMedium = headlineMedium.copy(fontFamily = Archivo),
        headlineSmall = headlineSmall.copy(fontFamily = Archivo),
        titleLarge = titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Archivo, fontSize = 18.sp),
        titleSmall = titleSmall.copy(fontFamily = Archivo),
        bodyLarge = AudiobookType.bodyEmpty,
        bodyMedium = bodyMedium.copy(fontFamily = Archivo, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = bodySmall.copy(fontFamily = Archivo),
        labelLarge = labelLarge.copy(fontFamily = Archivo),
        labelMedium = labelMedium.copy(fontFamily = Archivo),
        labelSmall = labelSmall.copy(fontFamily = Archivo),
    )
}
