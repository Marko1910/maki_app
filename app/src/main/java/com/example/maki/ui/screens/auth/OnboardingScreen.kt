package com.example.maki.ui.screens.auth

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
import com.example.maki.data.MakiLocation
import com.example.maki.ui.components.RumaLogo
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
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
    val labels = listOf("Comenzar", "Siguiente", "Comenzar a reciclar")

    // Lives here, not inside the pager page: swiping away must not lose the fix.
    var address by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }

    val finish: () -> Unit = { onFinish(address.trim().takeIf { it.isNotBlank() }, lat, lng) }

    // navigationBarsPadding: the last page's button sat under the system bar.
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm), horizontalArrangement = Arrangement.End) {
            Text("Saltar", color = MakiColors.Text2, style = MakiType.body,
                modifier = Modifier.clickable(onClickLabel = "Saltar introducción", role = Role.Button, onClick = finish)
                    .heightIn(min = MakiSpace.xl + MakiSpace.xxl)
                    .widthIn(min = MakiSpace.xl + MakiSpace.xxl).padding(vertical = MakiSpace.lg))
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

        Row(Modifier.fillMaxWidth().padding(bottom = MakiSpace.lg)
            .semantics { contentDescription = "Paso ${pager.currentPage + 1} de 3" }, horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                repeat(3) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) MakiSpace.xl else MakiSpace.sm, label = "dotW")
                    val c by animateColorAsState(if (active) MakiColors.Gen else MakiColors.Border, label = "dotC")
                    Box(Modifier.size(width = w, height = MakiSpace.sm).clip(CircleShape).background(c))
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(start = MakiSpace.gutter, end = MakiSpace.gutter, bottom = MakiSpace.lg * 2)) {
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

@Composable
private fun PageHello() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.stack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RumaLogo()
        Box(Modifier.height(MakiSpace.xl * 2))
        Text("Hola, soy Ruma", color = MakiColors.Text, style = MakiType.screenTitle)
        Box(Modifier.height(MakiSpace.sm))
        Text(
            "Tu agente de reciclaje inteligente. Te ayudo a reciclar en casa y ganar puntos por cada residuo.",
            color = MakiColors.Text2, style = MakiType.body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageHow() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.stack),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Así de fácil funciona", color = MakiColors.Text, style = MakiType.screenTitle)
        Box(Modifier.height(MakiSpace.xl))
        StepRow(1, Icons.Outlined.PhotoCamera, MakiColors.Gen, MakiColors.GenTint, "Toma una foto", "Captura tus residuos en casa")
        Box(Modifier.height(MakiSpace.md))
        StepRow(2, Icons.Filled.AutoAwesome, MakiColors.Rider, MakiColors.RiderTint, "Detectamos con IA", "Identificamos el material al instante")
        Box(Modifier.height(MakiSpace.md))
        StepRow(3, Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint, "Ganas puntos", "Canjéalos por dinero o premios")
    }
}

@Composable
private fun StepRow(n: Int, icon: ImageVector, tint: Color, bg: Color, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().makiCard().padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, bg, boxSize = MakiSpace.xl + MakiSpace.xxl, iconSize = MakiSpace.xl, radius = MakiRadius.badge)
        Column(Modifier.weight(1f).padding(end = MakiSpace.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(MakiSpace.xl).clip(CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
                    Text("$n", color = MakiColors.OnDark, style = MakiType.caption)
                }
                Text(title, color = MakiColors.Text, style = MakiType.cardTitle)
            }
            Text(subtitle, color = MakiColors.Text2, style = MakiType.bodySmall)
        }
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
            val fix = MakiLocation.current(context)
            state = when {
                fix == null -> LocateState.Unavailable
                else -> {
                    val line = MakiLocation.describe(context, fix)
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
        else if (MakiLocation.hasPermission(context)) locate()
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
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.stack),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¿Dónde vives?", color = MakiColors.Text, style = MakiType.screenTitle)
        Box(Modifier.height(MakiSpace.sm))
        Text(
            "Para enviarte al Eco-Rider más cercano y ubicarte en el ranking de tu cuadra.",
            color = MakiColors.Text2, style = MakiType.body,
        )
        Box(Modifier.height(MakiSpace.xl))

        MakiInputField(
            "TU DIRECCIÓN", Icons.Filled.Place, address, onAddress,
            placeholder = "Jr. Las Flores 123, SJL",
            trailing = {
                if (state == LocateState.Locating) {
                    CircularProgressIndicator(color = MakiColors.Gen, strokeWidth = MakiSpace.xs / 2, modifier = Modifier.size(MakiSpace.xl))
                } else {
                    Box(
                        Modifier.size(MakiSpace.xl + MakiSpace.xxl).clip(RoundedCornerShape(MakiRadius.badge))
                            .clickable(onClickLabel = "Usar mi ubicación", role = Role.Button, onClick = requestLocation),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MyLocation, "Usar mi ubicación", tint = MakiColors.Gen,
                            modifier = Modifier.size(MakiSpace.xl))
                    }
                }
            },
        )

        Box(Modifier.height(MakiSpace.sm))
        val (hint, tone) = when (state) {
            LocateState.Locating -> "Buscando tu ubicación…" to MakiColors.Text2
            LocateState.Found -> "Ubicación detectada. Ajústala si hace falta." to MakiColors.Success
            LocateState.Denied -> "Sin permiso de ubicación: escribe tu dirección." to MakiColors.Text2
            LocateState.Unavailable -> "No pudimos detectarla. Escríbela tú mismo." to MakiColors.Text2
            LocateState.Idle -> "Toca el ícono para usar tu ubicación actual." to MakiColors.Text2
        }
        Text(hint, color = tone, style = MakiType.label)

        Box(Modifier.height(MakiSpace.md))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(MakiRadius.chip)).background(MakiColors.SuccessTint).padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, null, tint = MakiColors.Success, modifier = Modifier.size(MakiSpace.lg))
            Text(
                "Usaremos tu ubicación solo para el recojo y el ranking vecinal. Puedes cambiarla cuando quieras.",
                color = MakiColors.Success, style = MakiType.label,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    MAKITheme { OnboardingScreen() }
}
