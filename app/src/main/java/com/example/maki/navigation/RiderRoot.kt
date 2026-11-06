package com.example.maki.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.maki.ui.screens.account.EditProfileScreen
import com.example.maki.ui.screens.account.AccountViewModel
import com.example.maki.data.MakiLocation
import com.example.maki.data.MakiRepository
import com.example.maki.ui.components.MakiNavBar
import com.example.maki.ui.components.MakiNavItem
import com.example.maki.ui.screens.rider.RiderBoardScreen
import com.example.maki.ui.screens.rider.RiderCompletedScreen
import com.example.maki.ui.screens.rider.RiderConfirmScreen
import com.example.maki.ui.screens.rider.RiderDashboardScreen
import com.example.maki.ui.screens.rider.RiderDeliveryScreen
import com.example.maki.ui.screens.rider.RiderEarningsScreen
import com.example.maki.ui.screens.rider.RiderHistoryScreen
import com.example.maki.ui.screens.rider.RiderNavigationScreen
import com.example.maki.ui.screens.rider.RiderNotificationsScreen
import com.example.maki.ui.screens.rider.RiderProfileScreen
import com.example.maki.ui.screens.rider.RiderRankingScreen
import com.example.maki.ui.screens.rider.RiderRouteScreen
import com.example.maki.ui.screens.rider.RiderStatsScreen
import com.example.maki.ui.screens.rider.RiderViewModel
import com.example.maki.ui.screens.market.MarketScreen
import com.example.maki.ui.screens.market.MarketViewModel
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hosts the Eco-Rider (recolector) navigation graph + bottom bar.
 *
 * Recojo flow: Inicio/Ruta → Navegación → Confirmar → Completado. Once a stop is
 * confirmed the flow collapses back to Inicio, so "back" can never land on the
 * confirm screen of a pickup that is already closed.
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
    val market: MarketViewModel = viewModel()
    val account: AccountViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var confirmLogout by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val ctx = LocalContext.current

    // The family's live map only works if this rider publishes where they are, so
    // ask on entry — it used to be asked only if the Ruta tab happened to open.
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) vm.reorderRoute(MakiLocation.lastKnown(ctx, maxAgeMs = FIX_MAX_AGE_MS))
    }
    LaunchedEffect(Unit) {
        if (!MakiLocation.hasPermission(ctx)) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Location heartbeat: bound to STARTED, so it stops when the app is not in
    // front of the rider — a delivery app tracks a courier, not a person's day.
    // Fresh fixes only: an hours-old cached one put a rider 500 km from his stops.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (MakiLocation.hasPermission(ctx)) {
                    MakiLocation.current(ctx, maxAgeMs = FIX_MAX_AGE_MS)?.let { fix ->
                        vm.onLocation(fix)
                        runCatching { MakiRepository.publishRiderLocation(fix.lat, fix.lng) }
                    }
                }
                delay(LOCATION_HEARTBEAT_MS)
            }
        }
    }

    // Surface VM messages (accept/confirm results, new dispatches) in the shared snackbar.
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    LaunchedEffect(account.message) {
        account.message?.let {
            snackbar.showSnackbar(it)
            account.consumeMessage()
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
                    onSelect = { route -> nav.switchTab(RiderRoutes.DASHBOARD, route) },
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = RiderRoutes.DASHBOARD,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner)
                // Without the bottom bar nothing else keeps a screen's last button off
                // the system navigation bar.
                .then(
                    if (currentRoute in RiderRoutes.bottomBarRoutes || currentRoute in RiderRoutes.edgeToEdgeRoutes) Modifier
                    else Modifier.navigationBarsPadding()
                ),
        ) {
            composable(RiderRoutes.DASHBOARD) {
                // Polling, not realtime: this is where a dispatched pickup shows up.
                LaunchedEffect(Unit) {
                    while (true) {
                        vm.refresh()
                        delay(DASHBOARD_POLL_MS)
                    }
                }
                RiderDashboardScreen(
                    state = vm.dashboard,
                    busy = vm.busy,
                    onNavigate = { route ->
                        // The "next stop" card claims the pickup before navigating to it.
                        if (route == RiderRoutes.NAVIGATION) {
                            vm.acceptNextStop { nav.navigate(RiderRoutes.NAVIGATION) }
                        } else {
                            nav.navigate(route)
                        }
                    },
                    onShiftChange = vm::setOnShift,
                    onPlanShift = { at -> vm.planShift(at, ctx) },
                )
            }
            composable(RiderRoutes.BOARD) {
                // Polling while the tab is open: a request that lands now is work now.
                LaunchedEffect(Unit) {
                    while (true) {
                        vm.loadBoard(force = true)
                        delay(BOARD_POLL_MS)
                    }
                }
                RiderBoardScreen(
                    state = vm.board,
                    busy = vm.busy,
                    error = vm.errors[RiderViewModel.KEY_BOARD],
                    onBack = { nav.switchTab(RiderRoutes.DASHBOARD, RiderRoutes.DASHBOARD) },
                    // Taking a stop puts it on the route: that is where it belongs now.
                    onClaim = { id -> vm.claimStop(id) { nav.navigate(RiderRoutes.ROUTE) } },
                    onRetry = { vm.loadBoard(force = true) },
                )
            }
            composable(RiderRoutes.ROUTE) {
                // The stop order starts where the rider is; without a fix it starts
                // at the first stop instead, and re-orders once one arrives.
                LaunchedEffect(Unit) { vm.loadRoute(MakiLocation.lastKnown(ctx, maxAgeMs = FIX_MAX_AGE_MS)) }
                RiderRouteScreen(
                    state = vm.route,
                    error = vm.errors[RiderViewModel.KEY_ROUTE],
                    onBack = { nav.popBackStack() },
                    onNavigateStop = { id ->
                        vm.selectStop(id)
                        nav.navigate(RiderRoutes.NAVIGATION)
                    },
                    onStartNavigation = {
                        vm.selectStop(null)
                        nav.navigate(RiderRoutes.NAVIGATION)
                    },
                    onRetry = { vm.loadRoute(force = true) },
                )
            }
            composable(RiderRoutes.MARKET) {
                LaunchedEffect(Unit) { market.load() }
                MarketScreen(
                    state = market.state,
                    accent = MakiColors.Rider,
                    error = market.error,
                    onRetry = { market.load(force = true) },
                    onOpenNews = { url -> openLink(ctx, url) },
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
                LaunchedEffect(Unit) { vm.loadProfile() }
                RiderProfileScreen(
                    state = vm.profile,
                    error = vm.errors[RiderViewModel.KEY_PROFILE],
                    onRetry = { vm.loadProfile(force = true) },
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) },
                    onInfo = notify,
                    onLogout = { confirmLogout = true },
                )
            }
            composable(RiderRoutes.NAVIGATION) {
                // Opening navigation is leaving for the stop: the family sees "va en camino".
                LaunchedEffect(Unit) { vm.startNavigation() }
                RiderNavigationScreen(
                    state = vm.navigation,
                    busy = vm.busy,
                    onBack = { nav.popBackStack() },
                    onArrived = {
                        vm.arrive()
                        nav.navigate(RiderRoutes.CONFIRM)
                    },
                    onPosition = { vm.onNavPosition(it) },
                    onRetry = vm::retryNavigation,
                    onRelease = { vm.releaseActive { nav.switchTab(RiderRoutes.DASHBOARD, RiderRoutes.DASHBOARD) } },
                )
            }
            composable(RiderRoutes.CONFIRM) {
                RiderConfirmScreen(
                    state = vm.confirm,
                    busy = vm.busy,
                    onBack = { nav.popBackStack() },
                    onAdjust = vm::adjustConfirmed,
                    onConfirm = {
                        vm.confirmCollection {
                            nav.navigate(RiderRoutes.COMPLETED) { popUpTo(RiderRoutes.DASHBOARD) }
                        }
                    },
                )
            }
            composable(RiderRoutes.COMPLETED) {
                RiderCompletedScreen(
                    state = vm.completed,
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
            composable(RiderRoutes.DELIVERY) {
                LaunchedEffect(Unit) { vm.loadDelivery() }
                RiderDeliveryScreen(
                    state = vm.delivery,
                    busy = vm.busy,
                    error = vm.errors[RiderViewModel.KEY_DELIVERY],
                    onBack = { nav.popBackStack() },
                    onToggleStop = vm::toggleDeliveryStop,
                    onPickCenter = vm::pickDeliveryCenter,
                    // La pantalla se queda: la entrega recien abierta aparece abajo,
                    // que es la unica senal de que la carga ya esta en el sistema.
                    onSend = { vm.sendDelivery {} },
                    onRetry = { vm.loadDelivery(force = true) },
                )
            }
            composable(RiderRoutes.EDIT_PROFILE) {
                LaunchedEffect(Unit) { account.load() }
                LaunchedEffect(account.saved) {
                    if (account.saved) {
                        account.consumeSaved()
                        vm.loadProfile(force = true)
                        nav.popBackStack()
                    }
                }
                EditProfileScreen(
                    state = account.state,
                    busy = account.busy,
                    error = account.error,
                    accent = MakiColors.Rider,
                    onBack = { nav.popBackStack() },
                    onName = account::onName,
                    onPhone = account::onPhone,
                    onSave = account::save,
                    onRetry = { account.load(force = true) },
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
                LaunchedEffect(Unit) { vm.loadNotifications(force = true) }
                RiderNotificationsScreen(
                    items = vm.notifications,
                    error = vm.errors[RiderViewModel.KEY_NOTIFICATIONS],
                    onBack = { nav.popBackStack() },
                    onRead = { id ->
                        vm.markNotificationRead(id)
                        // "Nuevo recojo asignado" should land where that stop is.
                        if (vm.notifications?.firstOrNull { it.id == id }?.aboutPickup == true) {
                            nav.navigate(RiderRoutes.ROUTE)
                        }
                    },
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
/** How often the dashboard re-reads stops, so a dispatched pickup appears on its own. */
private const val DASHBOARD_POLL_MS = 20_000L
/** How often the board tab re-reads the open requests while it is on screen. */
private const val BOARD_POLL_MS = 20_000L
/** A cached fix older than this is not "where the rider is now". */
private const val FIX_MAX_AGE_MS = 60_000L

/** The four bottom-bar tabs for the Eco-Rider flow. */
private val RiderNavItems = listOf(
    MakiNavItem(RiderRoutes.DASHBOARD, Icons.Filled.Home, "Inicio"),
    MakiNavItem(RiderRoutes.BOARD, Icons.Filled.Inventory2, "Tablero"),
    MakiNavItem(RiderRoutes.ROUTE, Icons.Filled.Map, "Ruta"),
    MakiNavItem(RiderRoutes.MARKET, Icons.Filled.Storefront, "Mercado"),
    MakiNavItem(RiderRoutes.PROFILE, Icons.Outlined.Person, "Perfil"),
)

/** Opens a news source in whatever browser the phone has; silent if it has none. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
        )
    }
}
