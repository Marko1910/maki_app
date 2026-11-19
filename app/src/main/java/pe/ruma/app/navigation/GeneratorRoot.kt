package pe.ruma.app.navigation

import pe.ruma.app.ui.screens.generator.GeneratorPickupsScreen
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import pe.ruma.app.ui.screens.generator.nextStep
import pe.ruma.app.ui.screens.account.EditProfileScreen
import pe.ruma.app.ui.screens.account.AccountViewModel
import pe.ruma.app.data.RumaLocation
import pe.ruma.app.data.RumaApi
import pe.ruma.app.data.RumaPrefs
import pe.ruma.app.data.RumaPush
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
import pe.ruma.app.ui.components.RumaNavBar
import pe.ruma.app.ui.components.RumaNavItem
import pe.ruma.app.ui.screens.generator.DispatchUi
import pe.ruma.app.ui.screens.generator.GeneratorCameraScreen
import pe.ruma.app.ui.screens.generator.GeneratorCentersScreen
import pe.ruma.app.ui.screens.generator.GeneratorChatScreen
import pe.ruma.app.ui.screens.generator.GeneratorHistoryScreen
import pe.ruma.app.ui.screens.generator.GeneratorHomeScreen
import pe.ruma.app.ui.screens.generator.GeneratorNotificationsScreen
import pe.ruma.app.ui.screens.generator.GeneratorPickupScreen
import pe.ruma.app.ui.screens.generator.GeneratorProfileScreen
import pe.ruma.app.ui.screens.generator.GeneratorRankingsScreen
import pe.ruma.app.ui.screens.generator.GeneratorResultScreen
import pe.ruma.app.ui.screens.generator.GeneratorTrackingScreen
import pe.ruma.app.ui.screens.generator.GeneratorViewModel
import pe.ruma.app.ui.screens.generator.GeneratorWalletScreen
import pe.ruma.app.ui.screens.generator.GeneratorInviteScreen
import pe.ruma.app.ui.screens.generator.GeneratorPlansScreen
import pe.ruma.app.ui.screens.generator.PlansViewModel
import pe.ruma.app.ui.screens.generator.InvitePrompt
import pe.ruma.app.ui.screens.generator.InviteViewModel
import pe.ruma.app.ui.screens.generator.HomeUiState
import pe.ruma.app.ui.screens.market.MarketScreen
import pe.ruma.app.ui.screens.market.MarketViewModel
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaFont
import kotlinx.coroutines.delay

/**
 * Hosts the Eco-Hero (generador) navigation graph + bottom bar.
 *
 * The scan → pickup flow is one-way: once a pickup is requested the family lands
 * back on Home (banner + next-step card show the pickup in flight), so "back" never
 * reopens the form it just submitted (or the camera before it).
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

    // Push: the permission is asked after "Confirmar recojo" (below), and a tapped pickup
    // notice (MainActivity -> RumaPush) opens that pickup's live map.
    val askNotifications = rememberNotificationAsk()
    LaunchedEffect(RumaPush.openPickupId) {
        RumaPush.openPickupId?.let { id ->
            RumaPush.consumeOpenPickup()
            vm.trackPickup(id)
            vm.loadTracking()
            nav.navigate(GenRoutes.TRACKING) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = RumaColors.Bg,
        // Screens own their top inset (statusBarsPadding); the bottom bar owns
        // its nav-bar inset. Scaffold only reserves the bottom bar's height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute in GenRoutes.bottomBarRoutes) {
                RumaNavBar(
                    items = GeneratorNavItems,
                    currentRoute = currentRoute,
                    accent = RumaColors.Gen,
                    onSelect = { route -> nav.switchTab(GenRoutes.HOME, route) },
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = GenRoutes.HOME,
            enterTransition = rumaEnter(GenRoutes.bottomBarRoutes), exitTransition = rumaExit(GenRoutes.bottomBarRoutes),
            popEnterTransition = rumaPopEnter(GenRoutes.bottomBarRoutes), popExitTransition = rumaPopExit(GenRoutes.bottomBarRoutes),
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
                var usedStep by remember { mutableStateOf(RumaPrefs.readUsedStep(RumaApi.session?.userId)) }
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
                // Offered only at a happy moment, and at most once a week (InvitePrompt keeps the date).
                val homeCtx = LocalContext.current
                var invitePrompt by remember { mutableStateOf(false) }
                val joy = vm.creditedGain > 0 || vm.week?.left == 0
                LaunchedEffect(joy) {
                    if (joy && !invitePrompt) invitePrompt = InvitePrompt.claim(homeCtx, pe.ruma.app.data.RumaApi.session?.userId)
                }
                GeneratorHomeScreen(
                    week = vm.week,
                    rumaDays = vm.rumaDays,
                    goal = vm.weeklyGoal,
                    loading = vm.homeLoading,
                    error = vm.homeError,
                    weekError = vm.weekError,
                    onRetry = { vm.loadHome() },
                    onGoalChange = vm::chooseWeeklyGoal,
                    creditedGain = vm.creditedGain,
                    onCreditedSeen = vm::creditedSeen,
                    // Once its button is used the card steps aside (it blocked the home's view) until a different step.
                    nextStep = nextStep(
                        liveTitle = h.activeTitle, liveLine = h.activeLine,
                        unpickedScan = vm.hasUnpickedScan, saved = vm.savedSummary,
                        balance = g.points, cashMin = vm.cashMin,
                        weekDays = vm.week?.days, weekLeft = vm.week?.left,
                    ).takeIf { it.key != usedStep },
                    onNextStepUsed = { usedStep = it.key; RumaPrefs.writeUsedStep(RumaApi.session?.userId, it.key) },
                    dispatch = vm.dispatch,
                    onDispatchSeen = vm::consumeDispatch,
                    invitePrompt = invitePrompt,
                    onInviteDismiss = { invitePrompt = false },
                    state = HomeUiState(
                        displayName = g.displayName,
                        ecoPoints = g.points,
                        pendingPoints = g.pendingPoints,
                        hasUnreadNotifications = vm.hasUnread,
                        riderIncoming = h.activeTitle != null,
                        riderTitle = h.activeTitle.orEmpty(),
                        riderEtaText = h.activeLine,
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
                    onDetected = { result, photo ->
                        vm.onDetectionSaved(result, photo)
                        // The scan is saved: back from the result goes Home, not into the camera.
                        nav.navigate(GenRoutes.RESULT) { popUpTo(GenRoutes.HOME) }
                    },
                    onInfo = { vm.notify(it) },
                )
            }
            composable(GenRoutes.RESULT) {
                GeneratorResultScreen(
                    result = vm.lastDetection,
                    photo = vm.lastScanPhoto,
                    quantities = vm.detectedQty,
                    onAdjust = { code, delta -> vm.adjustDetectedQty(code, delta) },
                    brandQuantities = vm.detectedBrands,
                    onAdjustBrand = { code, brand, delta -> vm.adjustBrandQty(code, brand, delta) },
                    onBack = { nav.popBackStack() },
                    onPedirRecojo = { nav.navigate(GenRoutes.PICKUP) },
                    onOtraFoto = { nav.navigate(GenRoutes.CAMERA) },
                    onGuardar = {
                        vm.notify("Guardado. Lo que escanees después se suma a tu ruma.")
                        nav.switchTab(GenRoutes.HOME, GenRoutes.HOME)
                    },
                )
            }
            composable(GenRoutes.MARKET) {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) { market.load() }
                MarketScreen(
                    state = market.state,
                    accent = RumaColors.Gen,
                    error = market.error,
                    onRetry = { market.load(force = true) },
                    onOpenNews = { url -> openLink(ctx, url) },
                    onAsk = { nav.navigate(GenRoutes.CHAT) },
                )
            }
            composable(GenRoutes.CHAT) { GeneratorChatScreen(onBack = { nav.popBackStack() }, onInfo = { vm.notify(it) }) }
            composable(GenRoutes.PROFILE) {
                LaunchedEffect(Unit) { vm.loadPickupForm() }
                GeneratorProfileScreen(
                    state = vm.state,
                    address = vm.pickupForm?.addressId?.let { vm.pickupForm?.addressLine }.orEmpty(),
                    onNavigate = { nav.navigate(it) },
                    onInfo = { vm.notify(it) },
                    onLogout = { confirmLogout = true },
                    onEditProfile = { nav.navigate(GenRoutes.EDIT_PROFILE) },
                    goal = vm.weeklyGoal,
                    onGoalChange = { days ->
                        vm.chooseWeeklyGoal(days)
                        vm.notify("Meta actualizada: $days ${if (days == 1) "día" else "días"} por semana.")
                    },
                )
            }
            composable(GenRoutes.PICKUP) {
                // The rider comes to where the phone is: get the fix as the form opens.
                // Without permission or signal the registered address still applies.
                val ctx = LocalContext.current
                var granted by remember { mutableStateOf(RumaLocation.hasPermission(ctx)) }
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
                    // No fresh fix indoors: the phone's last position from the past half hour is still its doorstep.
                    val fix = RumaLocation.current(ctx, maxAgeMs = 2 * 60_000L) ?: RumaLocation.lastKnown(ctx, maxAgeMs = 30 * 60_000L)
                    vm.setPickupLocation(fix, fix?.let { RumaLocation.describe(ctx, it) })
                    locating = false
                }
                val haptic = LocalHapticFeedback.current
                GeneratorPickupScreen(
                    form = vm.pickupForm,
                    sackCode = vm.state.sackCode,
                    busy = vm.busy,
                    gpsLine = vm.pickupHere?.let { vm.pickupHereLabel ?: "Tu ubicación actual" },
                    here = vm.pickupHere,
                    locating = locating,
                    onUseMyLocation = { if (granted) locateTick++ else askLocation.launch(locationPermissions) },
                    onBack = { nav.popBackStack() },
                    onConfirm = { selections, window, phone ->
                        vm.createPickup(selections, window, phone) {
                            askNotifications()   // now there is a rider to hear about
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.trackPickup(null)   // the one just asked for is the active one
                            // Back to Home, where the banner and the next-step card show the
                            // pickup in flight; nothing behind it reopens the form or the camera.
                            nav.switchTab(GenRoutes.HOME, GenRoutes.HOME)
                        }
                    },
                    onChangeAddress = { editAddress = true },
                )
            }
            composable(GenRoutes.WALLET) {
                LaunchedEffect(Unit) { vm.loadWallet() }
                val haptic = LocalHapticFeedback.current
                // What was just redeemed, for the "¿y ahora?" card; gone when the screen leaves.
                var redeemed by remember { mutableStateOf<String?>(null) }
                GeneratorWalletScreen(
                    state = vm.wallet,
                    busy = vm.busy,
                    pendingPoints = vm.state.pendingPoints,
                    error = vm.error,
                    onRetry = { vm.loadWallet() },
                    onScan = { nav.navigate(GenRoutes.CAMERA) },
                    onBack = { nav.popBackStack() },
                    onRedeem = { opt ->
                        vm.redeem(opt) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            redeemed = opt.title
                        }
                    },
                    justRedeemed = redeemed,
                    onHome = { nav.switchTab(GenRoutes.HOME, GenRoutes.HOME) },
                )
            }
            composable(GenRoutes.RANKINGS) {
                LaunchedEffect(Unit) { vm.loadRankings() }
                GeneratorRankingsScreen(state = vm.rankings, onBack = { nav.popBackStack() }, onScan = { nav.navigate(GenRoutes.CAMERA) })
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
                    // A new pickup always starts from a photo of the waste.
                    onNewPickup = { nav.navigate(GenRoutes.CAMERA) },
                    onOpenPickup = { id -> vm.trackPickup(id); vm.loadTracking() },
                    onOpenHistory = { nav.navigate(GenRoutes.PICKUPS) },
                    onWallet = { nav.navigate(GenRoutes.WALLET) },
                )
            }
            composable(GenRoutes.PICKUPS) {
                LaunchedEffect(Unit) { vm.loadPickupList() }
                GeneratorPickupsScreen(
                    pickups = vm.pickupList,
                    error = vm.error,
                    onRetry = { vm.loadPickupList() },
                    onBack = { nav.popBackStack() },
                    onOpen = { id ->
                        vm.trackPickup(id)
                        nav.navigate(GenRoutes.TRACKING) { popUpTo(GenRoutes.PICKUPS) { inclusive = true } }
                    },
                    onScan = { nav.navigate(GenRoutes.CAMERA) },
                )
            }
            composable(GenRoutes.CENTERS) {
                val ctx = LocalContext.current
                // Distances from the phone when it has a recent fix; the address otherwise.
                LaunchedEffect(Unit) { vm.loadCenters(RumaLocation.lastKnown(ctx, maxAgeMs = 10 * 60_000L)) }
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
                    onScan = { nav.navigate(GenRoutes.CAMERA) },
                    onRequestPickup = { nav.navigate(GenRoutes.CAMERA) },
                    onWallet = { nav.navigate(GenRoutes.WALLET) },
                    onOpenPickup = { pickupId ->
                        vm.trackPickup(pickupId)
                        nav.navigate(GenRoutes.TRACKING)
                    },
                )
            }
            composable(GenRoutes.INVITE) {
                val invite: InviteViewModel = viewModel()
                LaunchedEffect(Unit) { invite.load() }
                GeneratorInviteScreen(invite, onBack = { nav.popBackStack() })
            }
            composable(GenRoutes.PLANS) {
                val plans: PlansViewModel = viewModel()
                LaunchedEffect(Unit) { plans.load() }
                GeneratorPlansScreen(plans, onBack = { nav.popBackStack() })
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
                    accent = RumaColors.Gen,
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
        containerColor = RumaColors.Surface,
        title = { Text(title, fontFamily = RumaFont, fontWeight = FontWeight.ExtraBold, color = RumaColors.Text) },
        text = { Text(text, fontFamily = RumaFont, color = RumaColors.Text2) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, fontFamily = RumaFont, fontWeight = FontWeight.Bold, color = RumaColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, fontFamily = RumaFont, color = RumaColors.Text2) }
        },
    )
}


@Composable
private fun AddressDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var line by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RumaColors.Surface,
        title = { Text("Dirección de recojo", fontFamily = RumaFont, fontWeight = FontWeight.ExtraBold, color = RumaColors.Text) },
        text = {
            OutlinedTextField(
                value = line,
                onValueChange = { line = it },
                placeholder = { Text("Ej. Jr. Las Flores 123, SJL", fontFamily = RumaFont, color = RumaColors.Text2) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(line) }) {
                Text("Guardar", fontFamily = RumaFont, fontWeight = FontWeight.Bold, color = RumaColors.Gen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontFamily = RumaFont, color = RumaColors.Text2) }
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
    RumaNavItem(GenRoutes.HOME, Icons.Filled.Home, "Inicio"),
    RumaNavItem(GenRoutes.CAMERA, Icons.Outlined.PhotoCamera, "Cámara"),
    RumaNavItem(GenRoutes.MARKET, Icons.Filled.Storefront, "Mercado"),
    RumaNavItem(GenRoutes.PROFILE, Icons.Outlined.Person, "Perfil"),
)

/** Opens a news source in whatever browser the phone has; silent if it has none. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
        )
    }
}
