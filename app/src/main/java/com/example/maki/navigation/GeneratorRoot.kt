package com.example.maki.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import com.example.maki.ui.screens.account.EditProfileScreen
import com.example.maki.ui.screens.account.AccountViewModel
import com.example.maki.data.MakiLocation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.maki.ui.components.MakiNavBar
import com.example.maki.ui.components.MakiNavItem
import com.example.maki.ui.screens.generator.DispatchUi
import com.example.maki.ui.screens.generator.GeneratorCameraScreen
import com.example.maki.ui.screens.generator.GeneratorCentersScreen
import com.example.maki.ui.screens.generator.GeneratorChatScreen
import com.example.maki.ui.screens.generator.GeneratorHistoryScreen
import com.example.maki.ui.screens.generator.GeneratorHomeScreen
import com.example.maki.ui.screens.generator.GeneratorNotificationsScreen
import com.example.maki.ui.screens.generator.GeneratorPickupScreen
import com.example.maki.ui.screens.generator.GeneratorProfileScreen
import com.example.maki.ui.screens.generator.GeneratorRankingsScreen
import com.example.maki.ui.screens.generator.GeneratorResultScreen
import com.example.maki.ui.screens.generator.GeneratorTrackingScreen
import com.example.maki.ui.screens.generator.GeneratorViewModel
import com.example.maki.ui.screens.generator.GeneratorWalletScreen
import com.example.maki.ui.screens.generator.HomeUiState
import com.example.maki.ui.screens.market.MarketScreen
import com.example.maki.ui.screens.market.MarketViewModel
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.delay

/**
 * Hosts the Eco-Hero (generador) navigation graph + bottom bar.
 *
 * The scan → pickup flow is one-way: once a pickup is requested the family lands
 * on its live map with only Home behind it, so "back" never reopens the form it
 * just submitted (or the camera before it).
 *
 * [onLogout] is invoked (after the session is cleared) to leave the generator
 * graph entirely — the top-level [AppRoot] routes it back to the login screen.
 */
@Composable
fun GeneratorRoot(
    nav: NavHostController = rememberNavController(),
    onLogout: () -> Unit = {},
) {
    val vm: GeneratorViewModel = viewModel()
    val market: MarketViewModel = viewModel()
    val account: AccountViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var editAddress by remember { mutableStateOf(false) }

    // Surface VM messages (write results / info) as a snackbar, then clear.
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

    // A failed load used to leave a screen saying "Cargando…" forever: the error
    // went into the ViewModel and nothing ever read it. Say it out loud instead.
    LaunchedEffect(vm.error) {
        vm.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    Scaffold(
        containerColor = MakiColors.Bg,
        // Screens own their top inset (statusBarsPadding); the bottom bar owns
        // its nav-bar inset. Scaffold only reserves the bottom bar's height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute in GenRoutes.bottomBarRoutes) {
                MakiNavBar(
                    items = GeneratorNavItems,
                    currentRoute = currentRoute,
                    accent = MakiColors.Gen,
                    onSelect = { route -> nav.switchTab(GenRoutes.HOME, route) },
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = GenRoutes.HOME,
            // consumeWindowInsets keeps imePadding() in child screens (chat) from
            // double-counting the bottom-bar space the Scaffold already reserved.
            modifier = Modifier.padding(inner).consumeWindowInsets(inner)
                // Without the bottom bar nothing else keeps a screen's last button off
                // the system navigation bar (the pickup form's "Confirmar" sat under it).
                .then(
                    if (currentRoute in GenRoutes.bottomBarRoutes || currentRoute in GenRoutes.edgeToEdgeRoutes) Modifier
                    else Modifier.navigationBarsPadding()
                ),
        ) {
            composable(GenRoutes.HOME) {
                // Polling, not realtime: this card is how the family sees a rider
                // accept, arrive or finish while they were on another screen.
                LaunchedEffect(Unit) {
                    while (true) {
                        vm.loadHome()
                        delay(HOME_POLL_MS)
                    }
                }
                val g = vm.state
                val h = vm.home
                GeneratorHomeScreen(
                    state = HomeUiState(
                        displayName = g.displayName,
                        initials = g.initials,
                        streakDay = g.streakDays,
                        ecoPoints = g.points,
                        pendingPoints = g.pendingPoints,
                        moneyValue = g.moneyValue,
                        co2Avoided = g.co2,
                        hasUnreadNotifications = vm.hasUnread,
                        riderIncoming = h.activeTitle != null,
                        riderTitle = h.activeTitle.orEmpty(),
                        riderEtaText = h.activeLine,
                        blockRank = h.blockRank,
                        challengePct = h.challengePct,
                        challengeLabel = h.challengeName,
                        nextPickup = h.nextPickup,
                    ),
                    onNavigate = { route ->
                        // Home's proximity card means "the one happening now", so it
                        // drops any pickup a history row had pinned on the map.
                        if (route == GenRoutes.TRACKING) vm.trackPickup(null)
                        nav.navigate(route)
                    },
                )
            }
            composable(GenRoutes.CAMERA) {
                GeneratorCameraScreen(
                    onClose = { nav.popBackStack() },
                    onDetected = { result ->
                        vm.onDetectionSaved(result)
                        // The scan is saved: back from the result goes Home, not into the camera.
                        nav.navigate(GenRoutes.RESULT) { popUpTo(GenRoutes.HOME) }
                    },
                    onInfo = { vm.notify(it) },
                )
            }
            composable(GenRoutes.RESULT) {
                LaunchedEffect(Unit) { vm.loadChallenge() }
                GeneratorResultScreen(
                    result = vm.lastDetection,
                    challenge = vm.challenge,
                    quantities = vm.detectedQty,
                    onAdjust = { code, delta -> vm.adjustDetectedQty(code, delta) },
                    brandQuantities = vm.detectedBrands,
                    onAdjustBrand = { code, brand, delta -> vm.adjustBrandQty(code, brand, delta) },
                    onBack = { nav.popBackStack() },
                    onPedirRecojo = { nav.navigate(GenRoutes.PICKUP) },
                    onOtraFoto = { nav.navigate(GenRoutes.CAMERA) },
                )
            }
            composable(GenRoutes.MARKET) {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) { market.load() }
                MarketScreen(
                    state = market.state,
                    accent = MakiColors.Gen,
                    error = market.error,
                    onRetry = { market.load(force = true) },
                    onOpenNews = { url -> openLink(ctx, url) },
                )
            }
            composable(GenRoutes.CHAT) { GeneratorChatScreen(onBack = { nav.popBackStack() }, onInfo = { vm.notify(it) }) }
            composable(GenRoutes.PROFILE) {
                GeneratorProfileScreen(
                    state = vm.state,
                    onNavigate = { nav.navigate(it) },
                    onInfo = { vm.notify(it) },
                    onLogout = { confirmLogout = true },
                    onEditProfile = { nav.navigate(GenRoutes.EDIT_PROFILE) },
                )
            }
            composable(GenRoutes.PICKUP) {
                // The rider comes to where the phone is: get the fix as the form opens.
                // Without permission or signal the registered address still applies.
                val ctx = LocalContext.current
                var granted by remember { mutableStateOf(MakiLocation.hasPermission(ctx)) }
                var locateTick by remember { mutableIntStateOf(0) }
                var locating by remember { mutableStateOf(false) }
                val askLocation = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { grants ->
                    granted = grants.values.any { it }
                    if (!granted) vm.notify("Sin permiso de ubicación: usaremos tu dirección registrada.")
                }
                val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                LaunchedEffect(Unit) {
                    vm.loadPickupForm()
                    if (!granted) askLocation.launch(locationPermissions)
                }
                LaunchedEffect(granted, locateTick) {
                    if (!granted) return@LaunchedEffect
                    locating = true
                    // A fix up to 2 min old is still the same doorstep.
                    val fix = MakiLocation.current(ctx, maxAgeMs = 2 * 60_000L)
                    vm.setPickupLocation(fix, fix?.let { MakiLocation.describe(ctx, it) })
                    locating = false
                }
                GeneratorPickupScreen(
                    form = vm.pickupForm,
                    busy = vm.busy,
                    gpsLine = vm.pickupHere?.let { vm.pickupHereLabel ?: "Tu ubicación actual" },
                    locating = locating,
                    onUseMyLocation = { if (granted) locateTick++ else askLocation.launch(locationPermissions) },
                    onBack = { nav.popBackStack() },
                    onConfirm = { selections, window ->
                        vm.createPickup(selections, window) {
                            vm.trackPickup(null)   // the one just asked for is the active one
                            // Only Home stays behind the live map: back must not reopen
                            // the form that was just submitted.
                            nav.navigate(GenRoutes.TRACKING) { popUpTo(GenRoutes.HOME) }
                        }
                    },
                    onChangeAddress = { editAddress = true },
                )
            }
            composable(GenRoutes.WALLET) {
                LaunchedEffect(Unit) { vm.loadWallet() }
                GeneratorWalletScreen(
                    state = vm.wallet,
                    busy = vm.busy,
                    onBack = { nav.popBackStack() },
                    onRedeem = { vm.redeem(it) },
                )
            }
            composable(GenRoutes.RANKINGS) {
                LaunchedEffect(Unit) { vm.loadRankings() }
                GeneratorRankingsScreen(state = vm.rankings, onBack = { nav.popBackStack() })
            }
            composable(GenRoutes.TRACKING) {
                // Watch the rider approach: poll while this screen is on top, and
                // stop the moment it leaves — no background battery drain.
                LaunchedEffect(Unit) {
                    vm.loadTracking()
                    while (true) {
                        delay(TRACKING_POLL_MS)
                        vm.refreshTracking()
                    }
                }
                GeneratorTrackingScreen(
                    state = vm.tracking,
                    busy = vm.busy,
                    onBack = { nav.popBackStack() },
                    onCancel = { confirmCancel = true },
                )
            }
            composable(GenRoutes.CENTERS) {
                val ctx = LocalContext.current
                // Distances from the phone when it has a recent fix; the address otherwise.
                LaunchedEffect(Unit) { vm.loadCenters(MakiLocation.lastKnown(ctx, maxAgeMs = 10 * 60_000L)) }
                GeneratorCentersScreen(
                    centers = vm.centers,
                    prices = vm.priceRows,
                    onBack = { nav.popBackStack() },
                    onInfo = { vm.notify(it) },
                )
            }
            composable(GenRoutes.HISTORY) {
                LaunchedEffect(Unit) { vm.loadHistory() }
                GeneratorHistoryScreen(
                    state = vm.history,
                    onBack = { nav.popBackStack() },
                    onOpenPickup = { pickupId ->
                        vm.trackPickup(pickupId)
                        nav.navigate(GenRoutes.TRACKING)
                    },
                )
            }
            composable(GenRoutes.EDIT_PROFILE) {
                LaunchedEffect(Unit) { account.load() }
                LaunchedEffect(account.saved) {
                    // Guardar cierra la pantalla: quedarse en el formulario tras
                    // guardar deja dudando de si se guardo.
                    if (account.saved) { account.consumeSaved(); vm.refresh(); nav.popBackStack() }
                }
                EditProfileScreen(
                    state = account.state,
                    busy = account.busy,
                    error = account.error,
                    accent = MakiColors.Gen,
                    onBack = { nav.popBackStack() },
                    onName = account::onName,
                    onPhone = account::onPhone,
                    onSave = account::save,
                    onRetry = { account.load(force = true) },
                )
            }
            composable(GenRoutes.NOTIFICATIONS) {
                LaunchedEffect(Unit) { vm.loadNotifications() }
                GeneratorNotificationsScreen(
                    items = vm.notifications,
                    onBack = { nav.popBackStack() },
                    onMarkAllRead = { vm.markNotificationsRead() },
                    onCardClick = { id ->
                        vm.markNotificationRead(id)
                        // A pickup notice is only useful next to that pickup's live map.
                        if (vm.notifications?.firstOrNull { it.id == id }?.aboutPickup == true) {
                            // The row carries no pickup id, so this is the active one.
                            vm.trackPickup(null)
                            nav.navigate(GenRoutes.TRACKING)
                        }
                    },
                )
            }
        }
    }

    // "Un Eco-Rider va en camino" — the confirmation beat between asking for a
    // pickup and watching the map.
    vm.dispatch?.let { d ->
        DispatchDialog(dispatch = d, onOk = { vm.consumeDispatch() })
    }

    if (editAddress) {
        AddressDialog(
            initial = vm.pickupForm?.addressLine?.takeIf { it != "Sin dirección registrada" } ?: "",
            onSave = { line ->
                editAddress = false
                vm.saveAddress(line)
            },
            onDismiss = { editAddress = false },
        )
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancelar recojo",
            text = "El Eco-Rider dejará de venir y tus residuos no se recogerán. ¿Seguro?",
            confirm = "Sí, cancelar",
            dismiss = "Mantener",
            onConfirm = { confirmCancel = false; vm.cancelActivePickup() },
            onDismiss = { confirmCancel = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Cerrar sesión",
            text = "¿Seguro que quieres cerrar sesión?",
            confirm = "Cerrar sesión",
            dismiss = "Cancelar",
            onConfirm = {
                confirmLogout = false
                vm.signOut()
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** How often the tracking screen re-reads the rider's position. */
private const val TRACKING_POLL_MS = 10_000L
/** How often Home re-reads the pickup in flight + the bell. */
private const val HOME_POLL_MS = 30_000L

/** A destructive yes/no in the app's type and colours. */
@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MakiColors.Surface,
        title = { Text(title, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, color = MakiColors.Text) },
        text = { Text(text, fontFamily = MakiFont, color = MakiColors.Text2) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, fontFamily = MakiFont, fontWeight = FontWeight.Bold, color = MakiColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, fontFamily = MakiFont, color = MakiColors.Text2) }
        },
    )
}

@Composable
private fun DispatchDialog(dispatch: DispatchUi, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        containerColor = MakiColors.Surface,
        icon = {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MakiColors.GenTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike, null,
                    tint = MakiColors.Gen, modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                if (dispatch.searching) "Buscando un Eco-Rider" else "¡${dispatch.riderName} va en camino!",
                fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, color = MakiColors.Text,
            )
        },
        text = {
            Text(
                if (dispatch.searching) {
                    "Tu recojo quedó registrado. En cuanto un Eco-Rider lo tome, te avisamos y podrás seguirlo en el mapa."
                } else {
                    val eta = dispatch.etaMinutes?.let { " Llega en ~$it min." } ?: ""
                    "${dispatch.riderName} recogerá tu reciclaje.$eta Ten listos tus materiales; puedes seguirlo en el mapa."
                },
                fontFamily = MakiFont, color = MakiColors.Text2,
            )
        },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("Entendido", fontFamily = MakiFont, fontWeight = FontWeight.Bold, color = MakiColors.Gen)
            }
        },
    )
}

@Composable
private fun AddressDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var line by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MakiColors.Surface,
        title = { Text("Dirección de recojo", fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, color = MakiColors.Text) },
        text = {
            OutlinedTextField(
                value = line,
                onValueChange = { line = it },
                placeholder = { Text("Ej. Jr. Las Flores 123, SJL", fontFamily = MakiFont, color = MakiColors.Text2) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(line) }) {
                Text("Guardar", fontFamily = MakiFont, fontWeight = FontWeight.Bold, color = MakiColors.Gen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontFamily = MakiFont, color = MakiColors.Text2) }
        },
    )
}

/**
 * The four bottom-bar tabs for the Eco-Hero flow.
 *
 * Chat used to sit here as a fifth, but it hides the bar the moment it opens,
 * which is the tell that it is an action and not a destination. It lives on the
 * home screen now, on the card that already speaks in Ruma's voice.
 */
private val GeneratorNavItems = listOf(
    MakiNavItem(GenRoutes.HOME, Icons.Filled.Home, "Inicio"),
    MakiNavItem(GenRoutes.CAMERA, Icons.Outlined.PhotoCamera, "Cámara"),
    MakiNavItem(GenRoutes.MARKET, Icons.Filled.Storefront, "Mercado"),
    MakiNavItem(GenRoutes.PROFILE, Icons.Outlined.Person, "Perfil"),
)

/** Opens a news source in whatever browser the phone has; silent if it has none. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
        )
    }
}
