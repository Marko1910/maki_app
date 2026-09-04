package com.example.maki.navigation

// Route keys for the Eco-Hero (generador) flow.
object GenRoutes {
    const val HOME = "gen/home"
    const val CAMERA = "gen/camera"
    const val RESULT = "gen/result"
    const val CHAT = "gen/chat"
    const val PROFILE = "gen/profile"

    const val PICKUP = "gen/pickup"
    const val CENTERS = "gen/centers"
    const val WALLET = "gen/wallet"
    const val RANKINGS = "gen/rankings"
    const val TRACKING = "gen/tracking"
    const val HISTORY = "gen/history"
    const val NOTIFICATIONS = "gen/notifications"

    // Tabbed screens that keep the bottom navigation bar visible.
    // Cámara and Chat are immersive (own back/close), so the bar hides there.
    val bottomBarRoutes = setOf(HOME, PROFILE)
}
