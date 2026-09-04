package com.example.maki.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// MAKI design tokens (mirrors the Pencil variables 1:1)
// ============================================================
object MakiColors {
    // Brand / roles
    val Gen = Color(0xFF0F6E56)        // generador (Eco-Hero)
    val GenDark = Color(0xFF0B5544)
    val Rider = Color(0xFF185FA5)      // eco-rider
    val Admin = Color(0xFFD97A2B)      // centro de acopio

    // Surfaces
    val Bg = Color(0xFFF5F7F6)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E9E7)

    // Text
    val Text = Color(0xFF1A1A1A)
    val Text2 = Color(0xFF6B7280)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkSoft = Color(0xFFCDEAE0)  // soft mint used over the green gradient

    // Accents
    val Money = Color(0xFFBA7517)
    val Streak = Color(0xFFF26430)
    val Success = Color(0xFF1E8E4F)
    val Error = Color(0xFFD32F2F)

    // Frequently used tints (alpha over brand colors)
    val GenTint = Color(0x140F6E56)     // ~8%
    val GenTintStrong = Color(0x260F6E56)
    val RiderTint = Color(0x1A185FA5)
    val MoneyTint = Color(0x1ABA7517)
    val SuccessTint = Color(0x1A1E8E4F)
    val StreakTint = Color(0x1AF26430)
    val WhiteTint = Color(0x26FFFFFF)   // pills over the gradient card
}
