package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

data class RiderCompletedUiState(
    val stopName: String = "Fam. Nureña",
    val materialsSummary: String = "10 materiales · 1.5 kg",
    val pointsAwarded: String = "68 pts",
    val earnings: String = "S/2.50",
    val remaining: String = "Te quedan 3 paradas en tu ruta",
)

@Composable
fun RiderCompletedScreen(
    state: RiderCompletedUiState = RiderCompletedUiState(),
    onNext: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(0.4f))

        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MakiColors.SuccessTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.CheckCircle, null, tint = MakiColors.Success, modifier = Modifier.size(58.dp)) }

        Text("¡Recojo Completado!", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(
            "Registraste la recolección de ${state.stopName}",
            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        SoftCard(Modifier.fillMaxWidth(), gap = 12.dp) {
            SummaryRow(Icons.Filled.Inventory2, MakiColors.Rider, "Materiales", state.materialsSummary)
            SummaryRow(Icons.Filled.Star, MakiColors.Money, "Puntos al generador", state.pointsAwarded)
            SummaryRow(Icons.Filled.AccountBalanceWallet, MakiColors.Success, "Tus ganancias", state.earnings)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MakiColors.RiderTint).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Route, null, tint = MakiColors.Rider, modifier = Modifier.size(16.dp))
            Text(state.remaining, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton("Siguiente parada", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onNext)
        Text(
            "Volver al inicio",
            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinish).padding(8.dp),
        )
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Text(value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderCompletedPreview() {
    MAKITheme { RiderCompletedScreen() }
}
