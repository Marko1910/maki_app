package com.example.maki.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.maki.R

/** Static instances of the PWA fonts also render correctly on Android 7 (API 24). */
val MakiFont = FontFamily(
    Font(R.font.dm_sans_500, FontWeight.Medium),
    Font(R.font.dm_sans_800, FontWeight.ExtraBold),
)
val MakiDisplayFont = FontFamily(Font(R.font.bricolage_800, FontWeight.ExtraBold))

/**
 * The type scale, by the job each style does on screen — not by size.
 *
 * Every screen used to spell out `fontFamily`, `fontWeight` and `fontSize` on each
 * `Text` (390 of them), so a heading in one screen was 15sp Bold and the same heading
 * next door was 16sp ExtraBold. Naming the job is what keeps two screens consistent
 * without anybody remembering a number.
 *
 * Tracking is where this stops looking like a default: big figures get negative
 * letter spacing so they read as one block, and the small all-caps labels get positive
 * spacing so they stay legible once they are uppercase. `tnum` gives the metrics
 * fixed-width digits, so a balance does not jiggle while it counts up.
 */
object MakiType {
    private val digitos = "tnum"

    // Cinco tamanos y dos pesos. Antes habia once y cinco, y por eso ninguna
    // pantalla tenia jerarquia: con once tamanos todo compite y nada destaca.
    // Los nombres siguen siendo por oficio; lo que se colapso son los valores.

    /** The one number a screen is about: balance, month's earnings, points. */
    val metricHero = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.0).sp,
        fontFeatureSettings = digitos,
    )

    /** A figure that leads a card without owning the screen. */
    val metricLarge = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp,
        fontFeatureSettings = digitos,
    )

    /** The value inside a tile or a list row. */
    val metricMedium = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.4).sp,
        fontFeatureSettings = digitos,
    )

    /** Screen name in the header. */
    val screenTitle = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp,
    )

    /** The sentence that greets, or a question the screen asks. */
    val sectionHeading = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.4).sp,
    )

    /** Title of a card. */
    val cardTitle = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
    )

    /** Title of a row inside a list. */
    val rowTitle = TextStyle(
        fontFamily = MakiDisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
    )

    /** Running text. */
    val body = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 21.sp,
    )

    /** The explanatory line under a title. */
    val bodySmall = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp,
    )

    /** A field name, a date, a secondary figure. */
    val label = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp,
    )

    /** The smallest readable line: a code, a timestamp. */
    val caption = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp,
    )

    /** Section dividers, set in caps by [com.example.maki.ui.components.SectionLabel]. */
    val overline = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 1.0.sp,
    )

    val button = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
    )

    /** Pills and status chips. */
    val chip = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp, lineHeight = 16.sp,
    )

    /** Bottom navigation label. */
    val navLabel = TextStyle(
        fontFamily = MakiFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 15.sp,
    )
}

/**
 * The Material slots, so anything that reads `MaterialTheme.typography` (a dialog, a
 * text field) lands on the same scale. [MakiType] is what the screens use by name.
 */
val MakiTypography = Typography(
    headlineLarge = MakiType.metricHero,
    headlineMedium = MakiType.metricLarge,
    titleLarge = MakiType.screenTitle,
    titleMedium = MakiType.sectionHeading,
    titleSmall = MakiType.cardTitle,
    bodyLarge = MakiType.body,
    bodyMedium = MakiType.body,
    bodySmall = MakiType.bodySmall,
    labelLarge = MakiType.button,
    labelMedium = MakiType.label,
    labelSmall = MakiType.caption,
)
