package pe.ruma.app.ui.theme

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
object RumaSpace {
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
object RumaRadius {
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
object RumaElevation {
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
object RumaGradient {
    /** What every screen stands on. Vertical so it reads as light, not as art. */
    val screen: Brush = Brush.verticalGradient(
        listOf(RumaColors.BgTop, RumaColors.BgBottom),
    )

    val gen: Brush = Brush.linearGradient(listOf(RumaColors.Gen, RumaColors.GenLight))
    val genDeep: Brush = Brush.linearGradient(listOf(RumaColors.GenDark, RumaColors.Gen))
    val rider: Brush = Brush.linearGradient(listOf(RumaColors.Rider, RumaColors.RiderLight))
    val admin: Brush = Brush.linearGradient(listOf(RumaColors.Admin, RumaColors.AdminLight))
    val money: Brush = Brush.linearGradient(listOf(RumaColors.Money, RumaColors.MoneyLight))
    val success: Brush = Brush.linearGradient(listOf(RumaColors.Success, RumaColors.SuccessLight))

    /** The gradient for a role accent, so a shared screen does not need a `when`. */
    fun forAccent(accent: Color): Brush = when (accent) {
        RumaColors.Rider -> rider
        RumaColors.Admin -> admin
        RumaColors.Money -> money
        RumaColors.Success -> success
        else -> gen
    }

    /** The muted text colour that stays legible on top of [forAccent]. */
    fun softOn(accent: Color): Color = when (accent) {
        RumaColors.Rider -> RumaColors.OnRiderSoft
        RumaColors.Admin -> RumaColors.OnAdminSoft
        RumaColors.Money -> RumaColors.OnMoneySoft
        else -> RumaColors.OnGenSoft
    }

    /**
     * A primary button's fill: the role gradient for the two roles, flat for anything
     * else — [forAccent] falls back to green, and a red button must stay red.
     */
    fun button(color: Color): Brush = when (color) {
        RumaColors.Gen, RumaColors.Rider -> forAccent(color)
        else -> androidx.compose.ui.graphics.SolidColor(color)
    }

    /** The shadow a hero casts: its own colour, not a grey blur. */
    fun glowOf(accent: Color): Color = accent.copy(alpha = 0.25f)
}
