package com.brandonmiller.audiobookplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 defaults, with body and title sizes nudged up. Readability is the first
// design priority in PRD §21 and the app is often read at arm's length.
private val defaults = Typography()

val AudiobooksTypography = Typography(
    titleLarge = defaults.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleMedium = defaults.titleMedium.copy(fontSize = 18.sp),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = defaults.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
)
