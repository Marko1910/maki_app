package com.example.maki.navigation

// Route keys for the Eco-Rider (recolector) flow.
object RiderRoutes {
    const val DASHBOARD = "rider/dashboard"   // R1
    const val ROUTE = "rider/route"           // R2 Ruta Optimizada
    const val NAVIGATION = "rider/navigation"  // R3 Navegación (immersive)
    const val CONFIRM = "rider/confirm"        // R4 Confirmar Recolección
    const val COMPLETED = "rider/completed"    // R4b Recojo Completado
    const val RANKING = "rider/ranking"        // R5
    const val EARNINGS = "rider/earnings"      // R6 Ganancias
    const val PROFILE = "rider/profile"        // R7
    const val HISTORY = "rider/history"        // R8
    const val STATS = "rider/stats"            // R9 Estadísticas
    const val NOTIFICATIONS = "rider/notifications" // R10

    // Tabbed screens that keep the bottom navigation bar visible.
    // Navegación / Confirmar / Completado are immersive (own back/close).
    val bottomBarRoutes = setOf(DASHBOARD, ROUTE, HISTORY, PROFILE)
}
