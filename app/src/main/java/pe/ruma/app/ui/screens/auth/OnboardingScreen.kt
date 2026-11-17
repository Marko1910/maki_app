package pe.ruma.app.ui.screens.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.data.RumaLocation
import pe.ruma.app.ui.components.RumaLogo
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.IconBadge
import pe.ruma.app.ui.components.RumaInputField
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.SoftCard
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pe.ruma.app.R
import androidx.compose.material.icons.filled.Recycling
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import kotlinx.coroutines.launch

/**
 * First-run tour. The last page asks where the family lives — that address is what
 * dispatch measures rider distance against, so it is collected before the account
 * exists and handed to the sign-up screen ([onFinish]).
 */
@Composable
fun OnboardingScreen(onFinish: (address: String?, lat: Double?, lng: Double?) -> Unit = { _, _, _ -> }) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val labels = listOf("Comenzar", "Siguiente", "Continuar")

    // Lives here, not inside the pager page: swiping away must not lose the fix.
    var address by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }

    val finish: () -> Unit = { onFinish(address.trim().takeIf { it.isNotBlank() }, lat, lng) }

    // navigationBarsPadding: the last page's button sat under the system bar.
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.sm), horizontalArrangement = Arrangement.End) {
            TextAction("Saltar", RumaColors.Gen, "Saltar introducción", finish)
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> PageHello()
                1 -> PageHow()
                else -> PageWhere(
                    address = address,
                    onAddress = { address = it },
                    onLocated = { line, la, ln -> address = line; lat = la; lng = ln },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = RumaSpace.lg)
            .semantics { contentDescription = "Paso ${pager.currentPage + 1} de 3" }, horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                repeat(3) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) RumaSpace.xxl else RumaSpace.sm, label = "dotW")
                    val c by animateColorAsState(if (active) RumaColors.Gen else RumaColors.Hair, label = "dotC")
                    Box(Modifier.size(width = w, height = RumaSpace.sm).clip(CircleShape).background(c))
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(start = RumaSpace.gutter, end = RumaSpace.gutter, bottom = RumaSpace.lg * 2)) {
            PrimaryButton(
                labels[pager.currentPage], Modifier.fillMaxWidth(),
                icon = if (pager.currentPage == 2) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish()
                },
            )
        }
    }
}

/** The logo on a big glass panel with a "+ 240 pts" promise, then who Ruma is (PWA page 1). */
@Composable
private fun PageHello() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.stack),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().heightIn(min = 240.dp).rumaCard(RumaRadius.hero + RumaSpace.xs),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ruma_logo), contentDescription = "Ruma", modifier = Modifier.size(190.dp))
            StatusChip("+ 240 pts", RumaColors.Money, Modifier.align(Alignment.TopEnd).padding(RumaSpace.lg))
        }
        Text("Hola, soy Ruma", color = RumaColors.Text, style = RumaType.screenTitle)
        Text(
            "Tu agente de reciclaje inteligente. Te ayudo a reciclar en casa y ganar puntos por cada residuo.",
            color = RumaColors.Text2, style = RumaType.body,
        )
    }
}

/** Three steps, each a big number, its line and its icon; then what ten bottles pay. */
@Composable
private fun PageHow() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.stack),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xl),
    ) {
        Text("Así de fácil funciona", color = RumaColors.Text, style = RumaType.screenTitle)
        StepRow(1, Icons.Outlined.PhotoCamera, "Toma una foto", "Captura tus residuos en casa")
        StepRow(2, Icons.Filled.Recycling, "Detectamos con IA", "Identificamos el material al instante")
        StepRow(3, Icons.Filled.EmojiEvents, "Ganas puntos", "Canjéalos por dinero o premios")
        SoftCard(gap = RumaSpace.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("10 botellas PET", color = RumaColors.Text, style = RumaType.rowTitle, modifier = Modifier.weight(1f))
                StatusChip("+ 100 pts", RumaColors.Money)
            }
            Text("Equivale a S/0.10", color = RumaColors.Text2, style = RumaType.bodySmall)
        }
    }
}

@Composable
private fun StepRow(n: Int, icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$n", color = RumaColors.Text, style = RumaType.screenTitle, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = RumaSpace.xl + RumaSpace.xs))
        Column(Modifier.weight(1f)) {
            Text(title, color = RumaColors.Text, style = RumaType.rowTitle)
            Text(subtitle, color = RumaColors.Text2, style = RumaType.bodySmall)
        }
        GlassBadge(icon = icon)
    }
}

/** What the location step is currently doing — drives the hint under the field. */
private enum class LocateState { Idle, Locating, Found, Denied, Unavailable }

/**
 * Asks for the address, with the device fix as a shortcut rather than a
 * requirement: the permission is requested only when the user taps the target
 * icon, and typing the address by hand is always available (a denied permission,
 * location switched off, or no geocoder backend all land there).
 */
@Composable
private fun PageWhere(
    address: String,
    onAddress: (String) -> Unit,
    onLocated: (String, Double?, Double?) -> Unit,
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(LocateState.Idle) }

    val locate: () -> Unit = {
        state = LocateState.Locating
        scope.launch {
            val fix = RumaLocation.current(context)
            state = when {
                fix == null -> LocateState.Unavailable
                else -> {
                    val line = RumaLocation.describe(context, fix)
                    // No geocoder backend (common on AOSP builds): keep the point,
                    // let the user name the place themselves.
                    onLocated(line ?: address, fix.lat, fix.lng)
                    if (line == null) LocateState.Unavailable else LocateState.Found
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) locate() else state = LocateState.Denied
    }

    val requestLocation: () -> Unit = {
        if (inspection) Unit
        else if (RumaLocation.hasPermission(context)) locate()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }

    // Ask here, once, where the screen explains why: the GPS is the whole point of
    // this page. Granted, the fix fills the address by itself; refused, the page
    // still works by typing — and no other screen asks again.
    LaunchedEffect(Unit) {
        if (!inspection && state == LocateState.Idle) requestLocation()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.stack),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¿Dónde vives?", color = RumaColors.Text, style = RumaType.screenTitle)
        Box(Modifier.height(RumaSpace.sm))
        Text(
            "Para enviarte al Eco-Rider más cercano y ubicarte en el ranking de tu cuadra.",
            color = RumaColors.Text2, style = RumaType.body,
        )
        Box(Modifier.height(RumaSpace.xl))

        RumaInputField(
            "TU DIRECCIÓN", Icons.Filled.Place, address, onAddress,
            placeholder = "Jr. Las Flores 123, SJL",
            trailing = {
                if (state == LocateState.Locating) {
                    CircularProgressIndicator(color = RumaColors.Gen, strokeWidth = RumaSpace.xs / 2, modifier = Modifier.size(RumaSpace.xl))
                } else {
                    Box(
                        Modifier.size(RumaSpace.xl + RumaSpace.xxl).clip(RoundedCornerShape(RumaRadius.badge))
                            .clickable(onClickLabel = "Usar mi ubicación", role = Role.Button, onClick = requestLocation),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MyLocation, "Usar mi ubicación", tint = RumaColors.Gen,
                            modifier = Modifier.size(RumaSpace.xl))
                    }
                }
            },
        )

        Box(Modifier.height(RumaSpace.sm))
        val (hint, tone) = when (state) {
            LocateState.Locating -> "Buscando tu ubicación…" to RumaColors.Text2
            LocateState.Found -> "Ubicación detectada. Ajústala si hace falta." to RumaColors.Success
            LocateState.Denied -> "Sin permiso de ubicación: escribe tu dirección." to RumaColors.Text2
            LocateState.Unavailable -> "No pudimos detectarla. Escríbela tú mismo." to RumaColors.Text2
            LocateState.Idle -> "Toca el ícono para usar tu ubicación actual." to RumaColors.Text2
        }
        Text(hint, color = tone, style = RumaType.bodySmall)

        Box(Modifier.height(RumaSpace.md))
        Text(
            "Usaremos tu ubicación solo para el recojo y el ranking vecinal. Puedes cambiarla cuando quieras.",
            color = RumaColors.Text2, style = RumaType.bodySmall,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    RumaTheme { OnboardingScreen() }
}
