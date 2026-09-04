package com.example.maki.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The design uses Inter (weights 500/600/700/800). To stay pixel-faithful on
// glyphs, drop Inter*.ttf into res/font and swap this alias to that FontFamily.
// Until then we fall back to the platform sans-serif while keeping every
// weight / size / spacing decision from the design intact.
val MakiFont: FontFamily = FontFamily.Default

val MakiTypography = Typography(
    // headline (e.g. card values "4,250")
    headlineLarge = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp
    ),
)
