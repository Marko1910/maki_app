package com.example.maki.navigation

import androidx.navigation.NavHostController

/**
 * Bottom-bar tab switch shared by every role graph: preserves each tab's state
 * and avoids stacking duplicates. [start] is the graph's start destination.
 */
internal fun NavHostController.switchTab(start: String, route: String) {
    // The start tab is already at the bottom of the stack: just go back to it.
    // Navigating to it with popUpTo(start){saveState} + restoreState made
    // Navigation save the popped screens under the start's own key and restore
    // them straight away — "Volver al inicio" landed back on the screen it left.
    if (route == start) {
        popBackStack(start, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(start) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
