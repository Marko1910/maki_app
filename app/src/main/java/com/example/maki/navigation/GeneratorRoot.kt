package com.example.maki.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import com.example.maki.ui.components.MakiNavBar
import com.example.maki.ui.components.MakiNavItem
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
import com.example.maki.ui.screens.generator.DispatchUi
import com.example.maki.ui.screens.generator.HomeUiState
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

/**
 * Hosts the Eco-Hero (generador) navigation graph + bottom bar.
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
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var editAddress by remember { mutableStateOf(false) }

    // Surface VM messages (write results / info) as a snackbar, then clear.
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
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
                    tint = MakiColors.GenTint,
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
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(GenRoutes.HOME) {
                val g = vm.state
                GeneratorHomeScreen(
                    state = HomeUiState(
                        displayName = g.displayName,
                        initials = g.initials,
                        streakDay = g.streakDays,
                        ecoPoints = g.points,
                        moneyValue = g.moneyValue,
                        co2Avoided = g.co2,
                        hasUnreadNotifications = vm.hasUnread,
                    ),
                    onNavigate = { nav.navigate(it) },
                )
            }
            composable(GenRoutes.CAMERA) {
                GeneratorCameraScreen(
                    onClose = { nav.popBackStack() },
                    onDetected = { result ->
                        vm.onDetectionSaved(result)
                        nav.navigate(GenRoutes.RESULT)
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
                    onBack = { nav.popBackStack() },
                    onPedirRecojo = { nav.navigate(GenRoutes.PICKUP) },
                    onOtraFoto = { nav.navigate(GenRoutes.CAMERA) },
                )
            }
            composable(GenRoutes.CHAT) { GeneratorChatScreen(onBack = { nav.popBackStack() }, onInfo = { vm.notify(it) }) }
            composable(GenRoutes.PROFILE) {
                GeneratorProfileScreen(
                    state = vm.state,
                    onNavigate = { nav.navigate(it) },
                    onInfo = { vm.notify(it) },
                    onLogout = { confirmLogout = true },
                )
            }
            composable(GenRoutes.PICKUP) {
                LaunchedEffect(Unit) { vm.loadPickupForm() }
                GeneratorPickupScreen(
                    form = vm.pickupForm,
                    busy = vm.busy,
                    onBack = { nav.popBackStack() },
                    onConfirm = { selections, window ->
                        vm.createPickup(selections, window) { nav.navigate(GenRoutes.TRACKING) }
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
                    onComplete = { vm.completeActivePickup() },
                )
            }
            composable(GenRoutes.CENTERS) {
                LaunchedEffect(Unit) { vm.loadCenters() }
                GeneratorCentersScreen(
                    centers = vm.centers,
                    onBack = { nav.popBackStack() },
                    onInfo = { vm.notify(it) },
                )
            }
            composable(GenRoutes.HISTORY) {
                LaunchedEffect(Unit) { vm.loadHistory() }
                GeneratorHistoryScreen(state = vm.history, onBack = { nav.popBackStack() })
            }
            composable(GenRoutes.NOTIFICATIONS) {
                LaunchedEffect(Unit) { vm.loadNotifications() }
                GeneratorNotificationsScreen(
                    items = vm.notifications,
                    onBack = { nav.popBackStack() },
                    onMarkAllRead = { vm.markNotificationsRead() },
                    onCardClick = { vm.markNotificationRead(it) },
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

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = MakiColors.Surface,
            title = { Text("Cerrar sesión", fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, color = MakiColors.Text) },
            text = { Text("¿Seguro que quieres cerrar sesión?", fontFamily = MakiFont, color = MakiColors.Text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    vm.signOut()
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

/** How often the tracking screen re-reads the rider's position. */
private const val TRACKING_POLL_MS = 10_000L

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

/** The four bottom-bar tabs for the Eco-Hero flow. */
private val GeneratorNavItems = listOf(
    MakiNavItem(GenRoutes.HOME, Icons.Filled.Home, "Inicio"),
    MakiNavItem(GenRoutes.CAMERA, Icons.Outlined.PhotoCamera, "Cámara"),
    MakiNavItem(GenRoutes.CHAT, Icons.AutoMirrored.Filled.Chat, "Chat"),
    MakiNavItem(GenRoutes.PROFILE, Icons.Outlined.Person, "Perfil"),
)
