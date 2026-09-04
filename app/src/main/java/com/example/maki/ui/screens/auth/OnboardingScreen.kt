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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.data.MakiLocation
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
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

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp), horizontalArrangement = Arrangement.End) {
            Text("Saltar", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = finish))
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

        // Dots
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) 22.dp else 7.dp, label = "dotW")
                    val c by animateColorAsState(if (active) MakiColors.Gen else Color(0xFFCBD5D1), label = "dotC")
                    Box(Modifier.size(width = w, height = 7.dp).clip(CircleShape).background(c))
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 32.dp)) {
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
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.makiShadow(70.dp, 12.dp, Color(0x330F6E56)).size(140.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
        Box(Modifier.height(36.dp))
        Text("Hola, soy Maki", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        Box(Modifier.height(10.dp))
        Text(
            "Tu agente de reciclaje inteligente. Te ayudo a reciclar en casa y ganar puntos por cada residuo.",
            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            lineHeight = 20.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageHow() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Así de fácil funciona", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Box(Modifier.height(24.dp))
        StepRow(1, Icons.Outlined.PhotoCamera, MakiColors.Gen, MakiColors.GenTint, "Toma una foto", "Captura tus residuos en casa")
        Box(Modifier.height(14.dp))
        StepRow(2, Icons.Filled.AutoAwesome, MakiColors.Rider, MakiColors.RiderTint, "Detectamos con IA", "Identificamos el material al instante")
        Box(Modifier.height(14.dp))
        StepRow(3, Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint, "Ganas puntos", "Canjéalos por dinero o premios")
    }
}

@Composable
private fun StepRow(n: Int, icon: ImageVector, tint: Color, bg: Color, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().makiShadow(16.dp, 4.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, bg, boxSize = 46.dp, iconSize = 23.dp, radius = 14.dp)
        Column(Modifier.padding(end = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
                    Text("$n", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
                Text(title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Text(subtitle, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¿Dónde vives?", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Box(Modifier.height(6.dp))
        Text(
            "Para enviarte al Eco-Rider más cercano y ubicarte en el ranking de tu cuadra.",
            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Box(Modifier.height(18.dp))

        MakiInputField(
            "TU DIRECCIÓN", Icons.Filled.Place, address, onAddress,
            placeholder = "Jr. Las Flores 123, SJL",
            trailing = {
                if (state == LocateState.Locating) {
                    CircularProgressIndicator(color = MakiColors.Gen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        Icons.Filled.MyLocation, "Usar mi ubicación", tint = MakiColors.Gen,
                        modifier = Modifier.size(20.dp).clickable(onClick = requestLocation),
                    )
                }
            },
        )

        Box(Modifier.height(10.dp))
        val (hint, tone) = when (state) {
            LocateState.Locating -> "Buscando tu ubicación…" to MakiColors.Text2
            LocateState.Found -> "Ubicación detectada. Ajústala si hace falta." to MakiColors.Success
            LocateState.Denied -> "Sin permiso de ubicación: escribe tu dirección." to MakiColors.Text2
            LocateState.Unavailable -> "No pudimos detectarla. Escríbela tú mismo." to MakiColors.Text2
            LocateState.Idle -> "Toca el ícono para usar tu ubicación actual." to MakiColors.Text2
        }
        Text(hint, color = tone, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

        Box(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x121E8E4F)).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, null, tint = MakiColors.Success, modifier = Modifier.size(16.dp))
            Text(
                "Usaremos tu ubicación solo para el recojo y el ranking vecinal. Puedes cambiarla cuando quieras.",
                color = Color(0xFF15703E), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPreview() {
    MAKITheme { OnboardingScreen() }
}
