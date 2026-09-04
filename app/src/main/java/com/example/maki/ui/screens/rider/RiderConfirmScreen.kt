package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

data class ConfirmMaterialUi(val name: String, val detail: String, val points: String)

data class RiderConfirmUiState(
    val stopName: String = "Fam. Nureña",
    val address: String = "Jr. Las Flores 123",
    val streak: String = "Racha 21 días",
    val materials: List<ConfirmMaterialUi> = listOf(
        ConfirmMaterialUi("Plástico PET", "8 unidades · 1.2 kg", "+48 pts"),
        ConfirmMaterialUi("Latas de aluminio", "2 unidades · 0.3 kg", "+20 pts"),
    ),
    val totalWeight: String = "1.5 kg",
    val totalPoints: String = "68 pts",
    val earnings: String = "S/2.50",
)

@Composable
fun RiderConfirmScreen(
    state: RiderConfirmUiState = RiderConfirmUiState(),
    onBack: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DetailHeader("Confirmar Recolección", onBack)

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Generador being collected from.
            SoftCard(gap = 6.dp) {
                Text(state.stopName, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(14.dp))
                    Text(state.address, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = MakiColors.Streak, modifier = Modifier.size(14.dp))
                    Text(state.streak, color = MakiColors.Streak, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            SectionLabel("MATERIALES RECOLECTADOS")
            state.materials.forEach { MaterialRow(it) }

            // Totals.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MakiColors.RiderTint).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TotalRow(Icons.Filled.Inventory2, MakiColors.Rider, "Peso total", state.totalWeight)
                TotalRow(Icons.Filled.Star, MakiColors.Money, "Puntos al generador", state.totalPoints)
                TotalRow(Icons.Filled.AccountBalanceWallet, MakiColors.Success, "Ganarás", state.earnings)
            }

            PrimaryButton("Confirmar recolección", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onConfirm)
        }
    }
}

@Composable
private fun MaterialRow(material: ConfirmMaterialUi) {
    SoftCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Inventory2, MakiColors.Rider, MakiColors.RiderTint, boxSize = 40.dp, iconSize = 20.dp, radius = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(material.name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(material.detail, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Text(material.points, color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TotalRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Text(value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun RiderConfirmPreview() {
    MAKITheme { RiderConfirmScreen() }
}
