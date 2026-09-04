package com.example.maki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

/** Soft, brand-tinted elevation that mimics the blurred shadows in the design. */
fun Modifier.makiShadow(
    radius: Dp = 16.dp,
    elevation: Dp = 5.dp,
    color: Color = Color(0x141A1A1A),
): Modifier = this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(radius),
    clip = false,
    ambientColor = color,
    spotColor = color,
)

/** Filled brand button (component/BtnPrimary). */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = MakiColors.Gen,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .then(if (enabled) Modifier.makiShadow(14.dp, 6.dp, Color(0x400F6E56)) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else MakiColors.Border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Outlined brand button (component/BtnSecondary). */
@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MakiColors.Gen,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, color, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Rounded icon badge used throughout the cards. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    radius: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(radius))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** One bottom-nav tab: a route + its icon + label. */
data class MakiNavItem(val route: String, val icon: ImageVector, val label: String)

/**
 * Single reusable bottom navigation bar (mirrors Pencil's NavGenerador/NavRider).
 *
 * Driven entirely by [items] + the [currentRoute] from the host NavController, so
 * any flow (generador, rider, …) reuses it by passing its own tabs + [accent].
 * Place it once in the flow's Scaffold `bottomBar`; screens never embed it.
 */
@Composable
fun MakiNavBar(
    items: List<MakiNavItem>,
    currentRoute: String?,
    accent: Color,
    tint: Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .makiShadow(0.dp, 14.dp, Color(0x141A1A1A))
            .background(MakiColors.Surface)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) tint else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon, null,
                        tint = if (selected) accent else MakiColors.Text2,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Text(
                    item.label,
                    color = if (selected) accent else MakiColors.Text2,
                    fontFamily = MakiFont,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))

/**
 * The bordered, icon-led text field used across the auth screens (login, sign-up,
 * the onboarding address). Green border while it holds text, eye toggle for
 * passwords. [trailing] hangs an extra affordance off the right edge — the
 * "use my location" action on the address field.
 */
@Composable
fun MakiInputField(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visible: Boolean = true,
    onToggleVisible: (() -> Unit)? = null,
    enabled: Boolean = true,
    accent: Color = MakiColors.Gen,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        val filled = value.isNotEmpty()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MakiColors.Surface)
                .border(1.5.dp, if (filled) accent else MakiColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (filled) accent else MakiColors.Text2, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    textStyle = TextStyle(color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onToggleVisible != null) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    "Mostrar contraseña",
                    tint = MakiColors.Text2,
                    modifier = Modifier.size(19.dp).clickable(onClick = onToggleVisible),
                )
            }
            trailing?.invoke()
        }
    }
}
