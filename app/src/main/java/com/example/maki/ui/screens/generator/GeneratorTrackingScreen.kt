package com.example.maki.ui.screens.generator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.BackButton
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import org.osmdroid.util.GeoPoint

private val SampleTracking = TrackingUi(
    headerTitle = "Tu Eco-Rider está llegando",
    riderLine = "Carlos · Moto · ★ 4.8",
    etaText = "A 3 cuadras · ~5 min",
    itemsText = "8 PET + 2 latas",
    statusMessage = "Carlos ya salió. Llega en ~5 min. ¡Ten listos tus residuos!",
)

@Composable
fun GeneratorTrackingScreen(
    state: TrackingUi? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onComplete: () -> Unit = {},
    onCall: (() -> Unit)? = null,
) {
    val s = state ?: SampleTracking
    val context = LocalContext.current
    val call: () -> Unit = onCall ?: {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+51999888777")))
        }
        Unit
    }
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.headerTitle, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(s.riderLine, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        // Live map: the family's door and the rider closing in on it. Falls back to
        // the flat placeholder while we have no coordinates (an address typed by
        // hand, or a rider who has not reported a position yet).
        Box(Modifier.fillMaxWidth().height(430.dp).background(Color(0xFFE3EAE6))) {
            if (s.hasMap) {
                val home = GeoPoint(s.homeLat!!, s.homeLng!!)
                val rider = if (s.riderLat != null && s.riderLng != null) GeoPoint(s.riderLat, s.riderLng) else null
                OsmMap(
                    center = rider ?: home,
                    zoom = if (rider != null) 15.5 else 16.0,
                    markers = listOfNotNull(
                        OsmMarker(home, "Tu casa"),
                        rider?.let { OsmMarker(it, s.riderLine) },
                    ),
                    route = listOfNotNull(rider, home),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Place, null, tint = MakiColors.Gen, modifier = Modifier.size(34.dp))
                        Text(
                            "Ubicando a tu Eco-Rider…",
                            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        )
                    }
                }
            }
            // ETA chip
            Row(
                Modifier.align(Alignment.TopStart).padding(20.dp).makiShadow(16.dp, 5.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xF2FFFFFF)).padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MakiColors.Success))
                Text(
                    listOfNotNull(s.distanceText, s.etaText).joinToString(" · "),
                    color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                )
            }
        }

        // Info
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill(Modifier.weight(1f), Icons.Filled.Navigation, MakiColors.Rider, MakiColors.RiderTint, s.distanceText ?: s.etaText)
                InfoPill(Modifier.weight(1f), Icons.Filled.Inventory2, MakiColors.Gen, MakiColors.GenTint, s.itemsText)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0D0F6E56)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(s.statusMessage,
                    color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
            }
            PrimaryButton("Llamar al Eco-Rider", Modifier.fillMaxWidth(), icon = Icons.Filled.Phone, onClick = call)
            if (s.canComplete && state != null) {
                PrimaryButton(
                    "Confirmar recojo completado",
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.CheckCircle,
                    color = MakiColors.Success,
                    enabled = !busy,
                    onClick = onComplete,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(modifier: Modifier, icon: ImageVector, tint: Color, bg: Color, text: String) {
    Row(
        modifier.makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Text(text, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TrackingPreview() {
    MAKITheme { GeneratorTrackingScreen() }
}
