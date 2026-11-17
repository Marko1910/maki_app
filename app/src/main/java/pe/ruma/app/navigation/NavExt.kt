package pe.ruma.app.navigation

import android.content.res.Resources
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
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

/*
 * Screen transitions, defined once and handed to every NavHost (AppRoot, GeneratorRoot,
 * RiderRoot). Forward: the new screen fades in from a short slide on the right; back: the
 * reverse. Between two tabs it is a plain cross-fade: tabs are siblings, not a deeper step.
 * Compose scales these with the system animator setting, so "Quitar animaciones" turns them off.
 */
private const val NAV_IN_MS = 260
private const val NAV_OUT_MS = 200
private const val NAV_SLIDE_DP = 24

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(tabs: Set<String>) =
    initialState.destination.route in tabs && targetState.destination.route in tabs

private fun slidePx() =
    (NAV_SLIDE_DP * Resources.getSystem().displayMetrics.density).toInt()

internal fun rumaEnter(tabs: Set<String> = emptySet()): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (betweenTabs(tabs)) fadeIn(tween(NAV_IN_MS))
    else fadeIn(tween(NAV_IN_MS)) + slideInHorizontally(tween(NAV_IN_MS, easing = FastOutSlowInEasing)) { slidePx() }
}

internal fun rumaExit(tabs: Set<String> = emptySet()): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (betweenTabs(tabs)) fadeOut(tween(NAV_OUT_MS))
    else fadeOut(tween(NAV_OUT_MS)) + slideOutHorizontally(tween(NAV_OUT_MS)) { -slidePx() }
}

internal fun rumaPopEnter(tabs: Set<String> = emptySet()): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (betweenTabs(tabs)) fadeIn(tween(NAV_IN_MS))
    else fadeIn(tween(NAV_IN_MS)) + slideInHorizontally(tween(NAV_IN_MS, easing = FastOutSlowInEasing)) { -slidePx() }
}

internal fun rumaPopExit(tabs: Set<String> = emptySet()): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (betweenTabs(tabs)) fadeOut(tween(NAV_OUT_MS))
    else fadeOut(tween(NAV_OUT_MS)) + slideOutHorizontally(tween(NAV_OUT_MS)) { slidePx() }
}
