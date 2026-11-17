package pe.ruma.app.navigation

// Route keys for the Eco-Hero (generador) flow.
object GenRoutes {
    const val HOME = "gen/home"
    const val CAMERA = "gen/camera"
    const val RESULT = "gen/result"
    const val CHAT = "gen/chat"
    const val MARKET = "gen/market"
    const val PROFILE = "gen/profile"

    const val PICKUP = "gen/pickup"
    const val CENTERS = "gen/centers"
    const val WALLET = "gen/wallet"
    const val RANKINGS = "gen/rankings"
    const val TRACKING = "gen/tracking"
    const val HISTORY = "gen/history"
    const val PICKUPS = "gen/pickups"
    const val NOTIFICATIONS = "gen/notifications"
    const val EDIT_PROFILE = "gen/edit-profile"
    const val INVITE = "gen/invite"
    const val PLANS = "gen/plans"

    // Tabbed screens that keep the bottom navigation bar visible.
    // Cámara and Chat are immersive (own back/close), so the bar hides there.
    val bottomBarRoutes = setOf(HOME, MARKET, PROFILE)

    // Full-bleed screens that lay their own content around the system bars; every
    // other screen without the bottom bar gets the navigation-bar inset from the Root.
    val edgeToEdgeRoutes = setOf(CAMERA)
}
