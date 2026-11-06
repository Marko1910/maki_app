package com.example.maki.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// Ruma design tokens. Every colour the app paints lives here:
// a screen that declares its own Color(0x…) is a bug waiting to
// drift, which is how the same hero card ended up three greens.
// ============================================================
object MakiColors {
    // Brand / roles
    val Gen = Color(0xFF0F6E56)        // generador (the household)
    val GenDark = Color(0xFF0B5544)
    val GenLight = Color(0xFF18946F)   // second stop of the generador gradient
    val Rider = Color(0xFF185FA5)      // eco-rider
    val RiderLight = Color(0xFF1A8CDB)
    val Admin = Color(0xFFD97A2B)      // centro de acopio
    val AdminLight = Color(0xFFEFA14F)

    // Surfaces
    val Bg = Color(0xFFF7F5EF)
    // The screen is a soft tonal fall, not a flat fill. Four percent of
    // luminance is enough for a surface to read as sitting ON something;
    // more than that and it becomes a decorated page.
    val BgTop = Color(0xFFF7F5EF)
    val BgBottom = Color(0xFFEDF3EE)
    /** A card as a plane of light: it picks up the colour of what is under it. */
    val SurfaceGlass = Color(0x94FFFFFF)
    /** The lit top edge of that plane. One hairline, not a border. */
    val Hairline = Color(0xBFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    /** A card that sits inside another card, where white on white would vanish. */
    val SurfaceSoft = Color(0xFFEDF0EE)
    val Border = Color(0xFFE2E7E4)

    val PageAmber = Color(0xFFF2B84B)
    val RiderBgBottom = Color(0xFFEBF1F5)

    // Text
    val Text = Color(0xFF16211C)
    val Text2 = Color(0xFF5F6B64)
    /** Third level: a code, a timestamp, something present but not read. */
    val Text3 = Color(0xFF6B7670)
    val OnDark = Color(0xFFFFFFFF)

    // Muted text over each role's gradient. Readable without being white.
    val OnDarkSoft = Color(0xFFCDEAE0)  // kept: the original name for OnGenSoft
    val OnGenSoft = Color(0xFFCDEAE0)
    val OnRiderSoft = Color(0xFFD2E4F5)
    val OnAdminSoft = Color(0xFFFBE4CC)
    val OnMoneySoft = Color(0xFFF5E4C6)

    // Accents
    val Money = Color(0xFFBA7517)
    val MoneyLight = Color(0xFFD99A2B)
    val Streak = Color(0xFFF26430)
    val Success = Color(0xFF1E8E4F)
    val SuccessLight = Color(0xFF2FAE63)
    val Error = Color(0xFFD32F2F)

    // Frequently used tints (alpha over brand colors)
    val GenTint = Color(0x140F6E56)     // ~8%
    val GenTintStrong = Color(0x260F6E56)
    val RiderTint = Color(0x1A185FA5)
    val AdminTint = Color(0x1AD97A2B)
    val MoneyTint = Color(0x1ABA7517)
    val SuccessTint = Color(0x1A1E8E4F)
    val StreakTint = Color(0x1AF26430)
    val ErrorTint = Color(0x1AD32F2F)

    // Podium. Not brand colours, but everybody reads them, and the two ranking
    // screens were each inventing their own hex for the same three medals.
    val Gold = Color(0xFFE0A41B)
    val Silver = Color(0xFF9CA3AF)
    val Bronze = Color(0xFFC58B5B)
    // Over the camera viewfinder — the one place the app goes dark. These are
    // near-black on purpose: a role colour laid over live video stops being
    // readable, and Gen disappears entirely against grass or a green bin.
    val Ink = Color(0xFF0B0F0D)
    val Scrim = Color(0x59000000)        // a chip floating over the frame
    val ScrimStrong = Color(0xCC0B0F0D)  // a panel that must win over the frame
    val DetectOk = Color(0xFF34D399)     // detection box: brighter than Gen, to survive video
    val DetectWarn = Color(0xFFFBBF24)
    // Shadows. A near-black shadow over a tinted background reads as dirt, not
    // depth: this is the app's ink, the same dark green the neutrals come from.
    val Shadow = Color(0x140B2B22)
    val ShadowStrong = Color(0x240B2B22)
    // Over a gradient hero: the pills and the unfilled part of a progress bar.
    val WhiteTint = Color(0x26FFFFFF)
    val OnGradientTrack = Color(0x2EFFFFFF)
}
