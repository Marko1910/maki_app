package com.example.maki.navigation

import androidx.navigation.NavHostController

/**
 * Bottom-bar tab switch shared by every role graph: preserves each tab's state
 * and avoids stacking duplicates. [start] is the graph's start destination.
 */
internal fun NavHostController.switchTab(start: String, route: String) {
    navigate(route) {
        popUpTo(start) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
