package pe.ruma.app.navigation

// Route keys for the Eco-Rider (recolector) flow.
object RiderRoutes {
    const val DASHBOARD = "rider/dashboard"   // R1
    const val BOARD = "rider/board"           // R1b Tablero de solicitudes abiertas
    const val ROUTE = "rider/route"           // R2 Ruta Optimizada
    const val NAVIGATION = "rider/navigation"  // R3 Navegación (immersive)
    const val CONFIRM = "rider/confirm"        // R4 Confirmar Recolección
    const val COMPLETED = "rider/completed"    // R4b Recojo Completado
    const val DELIVERY = "rider/delivery"      // R4c Entregar en acopio
    const val RANKING = "rider/ranking"        // R5
    const val EARNINGS = "rider/earnings"      // R6 Ganancias
    const val PROFILE = "rider/profile"        // R7
    const val HISTORY = "rider/history"        // R8
    const val STATS = "rider/stats"            // R9 Estadísticas
    const val NOTIFICATIONS = "rider/notifications" // R10
    const val MARKET = "rider/market"          // R11 Mercado Vivo
    const val EDIT_PROFILE = "rider/edit-profile"

    // Tabbed screens that keep the bottom navigation bar visible. The board is a
    // tab because it is where the work comes from; the history moved into Perfil,
    // which is where you go to look something up, not to find your next stop.
    // Navegación / Confirmar / Completado are immersive (own back/close).
    val bottomBarRoutes = setOf(DASHBOARD, BOARD, ROUTE, MARKET, PROFILE)

    // Full-bleed screens that lay their own content around the system bars; every
    // other screen without the bottom bar gets the navigation-bar inset from the Root.
    val edgeToEdgeRoutes = setOf(NAVIGATION)
}
