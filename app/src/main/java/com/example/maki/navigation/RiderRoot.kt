package com.example.maki.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.Person
import com.example.maki.data.MakiRepository
import com.example.maki.ui.components.MakiNavBar
import com.example.maki.ui.components.MakiNavItem
import com.example.maki.ui.screens.rider.RiderCompletedScreen
import com.example.maki.ui.screens.rider.RiderConfirmScreen
import com.example.maki.ui.screens.rider.RiderDashboardScreen
import com.example.maki.ui.screens.rider.RiderEarningsScreen
import com.example.maki.ui.screens.rider.RiderHistoryScreen
import com.example.maki.ui.screens.rider.RiderNavigationScreen
import com.example.maki.ui.screens.rider.RiderNotificationsScreen
import com.example.maki.ui.screens.rider.RiderProfileScreen
import com.example.maki.ui.screens.rider.RiderRankingScreen
import com.example.maki.ui.screens.rider.RiderRouteScreen
import com.example.maki.ui.screens.rider.RiderStatsScreen
import com.example.maki.ui.screens.rider.RiderViewModel
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import androidx.compose.foundation.layout.consumeWindowInsets
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.maki.data.MakiLocation

/**
 * Hosts the Eco-Rider (recolector) navigation graph + bottom bar.
 *
 * [onLogout] is invoked (after the session is cleared) to leave the rider graph
 * entirely — the top-level [AppRoot] routes it back to the login screen.
 */
@Composable
fun RiderRoot(
    nav: NavHostController = rememberNavController(),
    onLogout: () -> Unit = {},
) {
    val vm: RiderViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var confirmLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // Location heartbeat: the family's tracking map is only live because the rider
    // publishes where they are. Bound to STARTED, so it stops when the app is not
    // in front of the rider — a delivery app tracks a courier, not a person's day.
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (MakiLocation.hasPermission(ctx)) {
                    MakiLocation.current(ctx)?.let { fix ->
                        runCatching { MakiRepository.publishRiderLocation(fix.lat, fix.lng) }
                    }
                }
                delay(LOCATION_HEARTBEAT_MS)
            }
        }
    }

    // Surface VM messages (accept/confirm results) in the shared snackbar.
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MakiColors.Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute in RiderRoutes.bottomBarRoutes) {
                MakiNavBar(
                    items = RiderNavItems,
                    currentRoute = currentRoute,
                    accent = MakiColors.Rider,
                    tint = MakiColors.RiderTint,
                    onSelect = { route -> nav.switchTab(RiderRoutes.DASHBOARD, route) },
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = RiderRoutes.DASHBOARD,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(RiderRoutes.DASHBOARD) {
                LaunchedEffect(Unit) { vm.refresh() }
                RiderDashboardScreen(
                    state = vm.dashboard,
                    onNavigate = { route ->
                        // The "next stop" card claims the pickup before navigating to it.
                        if (route == RiderRoutes.NAVIGATION) {
                            vm.acceptNextStop { nav.navigate(RiderRoutes.NAVIGATION) }
                        } else {
                            nav.navigate(route)
                        }
                    },
                )
            }
            composable(RiderRoutes.ROUTE) {
                // The stop order starts where the rider is; without permission it
                // simply starts at the first stop instead.
                val ctx = LocalContext.current
                var origin by remember { mutableStateOf(MakiLocation.lastKnown(ctx)) }
                val askLocation = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> if (granted) origin = MakiLocation.lastKnown(ctx) }

                LaunchedEffect(Unit) {
                    if (!MakiLocation.hasPermission(ctx)) {
                        askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    vm.loadRoute(origin)
                }
                LaunchedEffect(origin) { if (origin != null) vm.reorderRoute(origin) }
                RiderRouteScreen(
                    state = vm.route,
                    error = vm.errors[RiderViewModel.KEY_ROUTE],
                    onBack = { nav.popBackStack() },
                    onNavigateStop = { nav.navigate(RiderRoutes.NAVIGATION) },
                    onStartNavigation = { nav.navigate(RiderRoutes.NAVIGATION) },
                    onRetry = { vm.loadRoute(origin, force = true) },
                )
            }
            composable(RiderRoutes.HISTORY) {
                LaunchedEffect(Unit) { vm.loadHistory() }
                RiderHistoryScreen(
                    state = vm.history,
                    error = vm.errors[RiderViewModel.KEY_HISTORY],
                    onBack = { nav.popBackStack() },
                    onRetry = { vm.loadHistory(force = true) },
                )
            }
            composable(RiderRoutes.PROFILE) {
                RiderProfileScreen(
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) },
                    onInfo = notify,
                    onLogout = { confirmLogout = true },
                )
            }
            // Recojo flow: Navegación → Confirmar → Completado → vuelve al inicio.
            composable(RiderRoutes.NAVIGATION) {
                RiderNavigationScreen(
                    state = vm.navigation,
                    onBack = { nav.popBackStack() },
                    onArrived = {
                        vm.arrive()
                        nav.navigate(RiderRoutes.CONFIRM)
                    },
                )
            }
            composable(RiderRoutes.CONFIRM) {
                RiderConfirmScreen(
                    state = vm.confirm,
                    onBack = { nav.popBackStack() },
                    onConfirm = { vm.confirmCollection { nav.navigate(RiderRoutes.COMPLETED) } },
                )
            }
            composable(RiderRoutes.COMPLETED) {
                RiderCompletedScreen(
                    onNext = { nav.navigate(RiderRoutes.ROUTE) { popUpTo(RiderRoutes.DASHBOARD) } },
                    onFinish = { nav.switchTab(RiderRoutes.DASHBOARD, RiderRoutes.DASHBOARD) },
                )
            }
            composable(RiderRoutes.RANKING) {
                LaunchedEffect(Unit) { vm.loadRanking() }
                RiderRankingScreen(
                    state = vm.ranking,
                    error = vm.errors[RiderViewModel.KEY_RANKING],
                    onBack = { nav.popBackStack() },
                    onRetry = { vm.loadRanking(force = true) },
                )
            }
            composable(RiderRoutes.EARNINGS) {
                LaunchedEffect(Unit) { vm.loadEarnings() }
                RiderEarningsScreen(
                    state = vm.earnings,
                    error = vm.errors[RiderViewModel.KEY_EARNINGS],
                    onBack = { nav.popBackStack() },
                    onWithdraw = { notify("Retiro a Yape estará disponible pronto.") },
                    onRetry = { vm.loadEarnings(force = true) },
                )
            }
            composable(RiderRoutes.STATS) {
                LaunchedEffect(Unit) { vm.loadStats() }
                RiderStatsScreen(
                    state = vm.stats,
                    error = vm.errors[RiderViewModel.KEY_STATS],
                    onBack = { nav.popBackStack() },
                    onRetry = { vm.loadStats(force = true) },
                )
            }
            composable(RiderRoutes.NOTIFICATIONS) {
                LaunchedEffect(Unit) { vm.loadNotifications() }
                RiderNotificationsScreen(
                    items = vm.notifications,
                    error = vm.errors[RiderViewModel.KEY_NOTIFICATIONS],
                    onBack = { nav.popBackStack() },
                    onRead = { vm.markNotificationRead(it) },
                    onMarkAllRead = { vm.markAllNotificationsRead() },
                    onRetry = { vm.loadNotifications(force = true) },
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = MakiColors.Surface,
            title = { Text("Cerrar sesión", fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, color = MakiColors.Text) },
            text = { Text("¿Seguro que quieres cerrar sesión?", fontFamily = MakiFont, color = MakiColors.Text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    MakiRepository.signOut()
                    onLogout()
                }) { Text("Cerrar sesión", fontFamily = MakiFont, fontWeight = FontWeight.Bold, color = MakiColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Cancelar", fontFamily = MakiFont, color = MakiColors.Text2)
                }
            },
        )
    }
}

/** How often the rider republishes their position while the app is in front of them. */
private const val LOCATION_HEARTBEAT_MS = 15_000L

/** The four bottom-bar tabs for the Eco-Rider flow. */
private val RiderNavItems = listOf(
    MakiNavItem(RiderRoutes.DASHBOARD, Icons.Filled.Home, "Inicio"),
    MakiNavItem(RiderRoutes.ROUTE, Icons.Filled.Map, "Ruta"),
    MakiNavItem(RiderRoutes.HISTORY, Icons.AutoMirrored.Filled.Assignment, "Historial"),
    MakiNavItem(RiderRoutes.PROFILE, Icons.Outlined.Person, "Perfil"),
)
