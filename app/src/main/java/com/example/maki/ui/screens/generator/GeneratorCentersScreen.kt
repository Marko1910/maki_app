package com.example.maki.ui.screens.generator

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import org.osmdroid.util.GeoPoint

/** Nearby collection centers on an OSM map + a list with directions/call actions. */
@Composable
fun GeneratorCentersScreen(
    centers: List<CenterUi>? = null,
    onBack: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val withCoords = centers.orEmpty().filter { it.lat != null && it.lng != null }
    val mapCenter = withCoords.firstOrNull()?.let { GeoPoint(it.lat!!, it.lng!!) }
        ?: GeoPoint(-12.0464, -77.0428) // Lima

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader("Puntos de Acopio", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)) {
                OsmMap(
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize(),
                    zoom = 12.0,
                    markers = withCoords.map { OsmMarker(GeoPoint(it.lat!!, it.lng!!), it.name) },
                )
            }
            when {
                centers == null -> Text("Cargando puntos de acopio…", color = MakiColors.Text2, fontFamily = MakiFont, fontSize = 14.sp)
                centers.isEmpty() -> Text("Aún no hay puntos de acopio registrados cerca.", color = MakiColors.Text2, fontFamily = MakiFont, fontSize = 14.sp)
                else -> centers.forEach { c ->
                    CenterCard(c, onRoute = { openInMaps(context, c, onInfo) }, onCall = { dial(context, c.phone, onInfo) })
                }
            }
        }
    }
}

@Composable
private fun CenterCard(c: CenterUi, onRoute: () -> Unit, onCall: () -> Unit) {
    SoftCard(gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            OpenBadge(c.open)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(15.dp))
            Text(c.address, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Recycling, null, tint = MakiColors.Gen, modifier = Modifier.size(15.dp))
            Text("Recibe: ${c.materials}", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cómo llegar", Modifier.weight(1f), icon = Icons.Filled.Directions, onClick = onRoute)
            SecondaryButton("Llamar", Modifier.weight(1f), icon = Icons.Filled.Phone, onClick = onCall)
        }
    }
}

@Composable
private fun OpenBadge(open: Boolean) {
    val bg = if (open) MakiColors.SuccessTint else MakiColors.Border
    val fg = if (open) MakiColors.Success else MakiColors.Text2
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(if (open) "Abierto" else "Cerrado", color = fg, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
    }
}

private fun openInMaps(context: Context, c: CenterUi, onInfo: (String) -> Unit) {
    if (c.lat == null || c.lng == null) { onInfo("Este centro no tiene ubicación en el mapa."); return }
    val uri = Uri.parse("geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}(${Uri.encode(c.name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { onInfo("No hay una app de mapas instalada.") }
}

private fun dial(context: Context, phone: String?, onInfo: (String) -> Unit) {
    if (phone.isNullOrBlank()) { onInfo("Este centro no tiene teléfono registrado."); return }
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        .onFailure { onInfo("No se pudo abrir el marcador.") }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun CentersPreview() {
    MAKITheme {
        GeneratorCentersScreen(
            centers = listOf(
                CenterUi("Centro de Acopio SJL Norte", "Av. Próceres 800, SJL", true, "PET, Aluminio, Cartón", -12.0, -77.0, "01-555-0100"),
                CenterUi("Recicladora Los Olivos", "Av. Universitaria 1500", false, "PET, Vidrio", -12.0, -77.0, null),
            ),
        )
    }
}
