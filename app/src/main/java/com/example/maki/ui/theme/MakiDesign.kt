package com.example.maki.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing. Six steps, and screens pick a step instead of a number.
 *
 * The app had 14, 16, 20 and 22 dp used interchangeably for the same job, which is why
 * two cards side by side never quite lined up.
 */
object MakiSpace {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    /** The side margin every screen shares; the one number that aligns everything. */
    val gutter: Dp = 16.dp

    /** Vertical gap between cards in a scrolling screen. */
    val stack: Dp = 16.dp
}

/** Corner radii, by the size of the thing being rounded. */
object MakiRadius {
    val chip: Dp = 8.dp
    val badge: Dp = 14.dp
    val card: Dp = 16.dp
    val hero: Dp = 20.dp
    val pill: Dp = 100.dp
}

/**
 * Shadow depth. Only three: a surface is either flat on the background, lifted as a
 * card, or the one hero the screen is about.
 */
object MakiElevation {
    val flat: Dp = 0.dp
    val card: Dp = 0.dp
    val hero: Dp = 0.dp
}

/**
 * The role gradients.
 *
 * Twelve screens each built their own `Brush.linearGradient`, with a second stop
 * invented on the spot, so the generator's hero card was a different green on the
 * home screen than in the wallet. One brush per role, defined once.
 */
object MakiGradient {
    /** What every screen stands on. Vertical so it reads as light, not as art. */
    val screen: Brush = Brush.verticalGradient(
        listOf(MakiColors.BgTop, MakiColors.BgBottom),
    )

    val gen: Brush = Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenLight))
    val genDeep: Brush = Brush.linearGradient(listOf(MakiColors.GenDark, MakiColors.Gen))
    val rider: Brush = Brush.linearGradient(listOf(MakiColors.Rider, MakiColors.RiderLight))
    val admin: Brush = Brush.linearGradient(listOf(MakiColors.Admin, MakiColors.AdminLight))
    val money: Brush = Brush.linearGradient(listOf(MakiColors.Money, MakiColors.MoneyLight))
    val success: Brush = Brush.linearGradient(listOf(MakiColors.Success, MakiColors.SuccessLight))

    /** The gradient for a role accent, so a shared screen does not need a `when`. */
    fun forAccent(accent: Color): Brush = when (accent) {
        MakiColors.Rider -> rider
        MakiColors.Admin -> admin
        MakiColors.Money -> money
        MakiColors.Success -> success
        else -> gen
    }

    /** The muted text colour that stays legible on top of [forAccent]. */
    fun softOn(accent: Color): Color = when (accent) {
        MakiColors.Rider -> MakiColors.OnRiderSoft
        MakiColors.Admin -> MakiColors.OnAdminSoft
        MakiColors.Money -> MakiColors.OnMoneySoft
        else -> MakiColors.OnGenSoft
    }

    /** The shadow a hero casts: its own colour, not a grey blur. */
    fun glowOf(accent: Color): Color = accent.copy(alpha = 0.25f)
}
