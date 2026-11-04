package com.example.maki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiElevation
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiType

/** Keeping shadows here prevents each screen from inventing its own elevation. */
fun Modifier.makiShadow(
    radius: Dp = MakiRadius.card,
    elevation: Dp = MakiElevation.card,
    color: Color = MakiColors.Shadow,
): Modifier = this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(radius),
    clip = false,
    ambientColor = color,
    spotColor = color,
)

/**
 * The background every screen stands on.
 *
 * Thirty screens each painted it themselves, so the day the background stops
 * being one flat colour is thirty edits instead of one.
 */
fun Modifier.makiScreen(): Modifier = this.background(MakiGradient.screen)
fun Modifier.makiCard(radius: Dp = MakiRadius.card): Modifier = this
    .makiShadow(radius, MakiElevation.card)
    .clip(RoundedCornerShape(radius))
    .background(MakiColors.SurfaceGlass)
    // The hairline is the light catching the top edge. Without it a translucent
    // surface has no edge at all and the card dissolves into the background.
    .border(1.dp, MakiColors.Hairline, RoundedCornerShape(radius))

fun Modifier.makiHero(
    brush: Brush,
    radius: Dp = MakiRadius.hero,
    glow: Color,
): Modifier = this
    .makiShadow(radius, MakiElevation.hero, glow)
    .clip(RoundedCornerShape(radius))
    .background(brush)

fun Modifier.makiOutlined(color: Color, radius: Dp = MakiRadius.card): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(MakiColors.Surface)
    .border(1.5.dp, color, RoundedCornerShape(radius))

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
            // A fixed green shadow made rider buttons keep the generator's glow.
            .then(if (enabled) Modifier.makiShadow(MakiRadius.badge, MakiElevation.card, MakiGradient.glowOf(color)) else Modifier)
            .clip(RoundedCornerShape(MakiRadius.badge))
            .background(if (enabled) color else MakiColors.Border)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = MakiSpace.lg, horizontal = MakiSpace.xl),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, style = MakiType.button)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MakiColors.Gen,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .makiOutlined(color, MakiRadius.badge)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = MakiSpace.lg, horizontal = MakiSpace.xl),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, style = MakiType.button)
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    radius: Dp = MakiRadius.badge,
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

data class MakiNavItem(val route: String, val icon: ImageVector, val label: String)

/**
 * Sharing navigation between role roots keeps destination styling consistent.
 * Place it once in the flow's Scaffold `bottomBar` to avoid duplicate screen bars.
 */
@Composable
fun MakiNavBar(
    items: List<MakiNavItem>,
    currentRoute: String?,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .makiShadow(0.dp, MakiElevation.hero)
            .background(MakiColors.Surface)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            // The whole tab lights up, icon and label inside one accent pill: a
            // tinted icon alone was easy to miss at a glance on the street.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(MakiRadius.card))
                    .background(if (selected) accent else Color.Transparent)
                    .semantics { this.selected = selected }
                    .clickable(onClickLabel = "Ir a ${item.label}", role = Role.Tab) { onSelect(item.route) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = MakiSpace.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MakiSpace.xs),
            ) {
                Icon(
                    item.icon, null,
                    tint = if (selected) Color.White else MakiColors.Text2,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    item.label,
                    color = if (selected) Color.White else MakiColors.Text2,
                    style = MakiType.navLabel,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(MakiSpace.sm))

/**
 * [trailing] lets address fields offer location lookup without duplicating the
 * shared input styling or password behavior across authentication screens.
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
        Text(label, color = MakiColors.Text2, style = MakiType.label)
        val filled = value.isNotEmpty()
        Row(
            Modifier.fillMaxWidth().makiOutlined(if (filled) accent else MakiColors.Border, MakiRadius.badge)
                .heightIn(min = 48.dp)
                .padding(horizontal = MakiSpace.lg, vertical = MakiSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (filled) accent else MakiColors.Text2, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = MakiColors.Text2, style = MakiType.body)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    textStyle = MakiType.body.copy(color = MakiColors.Text),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (onToggleVisible != null) {
                // The old eye always announced "show", even when it hid the password.
                val toggleLabel = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(MakiRadius.badge))
                        .clickable(enabled = enabled, onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleVisible),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        toggleLabel,
                        tint = MakiColors.Text2,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun HeroCard(
    accent: Color,
    overline: String,
    metric: String,
    support: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth()
            .makiHero(MakiGradient.forAccent(accent), glow = MakiGradient.glowOf(accent))
            .padding(MakiSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Text(overline, color = MakiGradient.softOn(accent), style = MakiType.overline)
        Text(metric, color = Color.White, style = MakiType.metricHero)
        if (support != null) {
            Text(support, color = MakiGradient.softOn(accent), style = MakiType.bodySmall)
        }
        content()
    }
}

@Composable
fun MetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // Height is equalised by [MetricRow]'s intrinsic measure, not by a fixed
    // floor: a floor only sets a minimum, so one wrapping label still pushed its
    // tile taller than its neighbours.
    Column(
        modifier.fillMaxHeight().makiCard()
            .then(
                if (onClick != null) Modifier.clickable(
                    onClickLabel = "Ver $label", role = Role.Button, onClick = onClick,
                ) else Modifier
            )
            .heightIn(min = 112.dp)
            .padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        // El icono es orientacion, no mensaje: en neutro deja que la cifra
        // sea lo unico que destaca dentro de la tarjeta.
        IconBadge(icon, MakiColors.Text2, MakiColors.SurfaceSoft)
        // Dos lineas antes que recortar: a escala de fuente 1.3 el recorte
        // convertia "1,241 kg" en "1,2...", que es esconder el dato.
        Text(
            value, color = MakiColors.Text, style = MakiType.metricMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(label, color = MakiColors.Text2, style = MakiType.bodySmall, maxLines = 2)
    }
}

/**
 * A row of [MetricTile]s that all end up the same height.
 *
 * `IntrinsicSize.Min` measures the tallest tile first and hands that height to
 * the rest, which is the only thing that keeps the grid level once a label wraps
 * at a large system font scale.
 */
@Composable
fun MetricRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        content = content,
    )
}

@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accent: Color = MakiColors.Gen,
    trailing: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .makiCard()
            .then(
                if (enabled) Modifier.clickable(
                    onClickLabel = "Ir a $title", role = Role.Button, onClick = onClick,
                ) else Modifier.semantics(mergeDescendants = true) { disabled() }
            )
            .heightIn(min = 48.dp)
            .padding(MakiSpace.md),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cinco filas con cinco insignias de color distinto son un arcoiris,
        // no una jerarquia: el icono orienta, el texto es lo que se lee.
        IconBadge(icon, MakiColors.Text2, MakiColors.SurfaceSoft)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Text(title, color = MakiColors.Text, style = MakiType.rowTitle)
            if (subtitle != null) {
                Text(subtitle, color = MakiColors.Text2, style = MakiType.bodySmall)
            }
        }
        if (trailing != null) {
            Text(trailing, color = accent, style = MakiType.label)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MakiColors.Text3, modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(MakiRadius.chip))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = MakiSpace.sm, vertical = MakiSpace.xs),
        color = color,
        style = MakiType.chip,
    )
}

@Composable
fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = MakiColors.OnGradientTrack,
    fill: Color = Color.White,
) {
    val boundedProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(8.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(boundedProgress, 0f..1f) }
            .clip(RoundedCornerShape(MakiRadius.pill))
            .background(track),
    ) {
        Box(
            Modifier.fillMaxWidth(boundedProgress).fillMaxHeight()
                .clip(RoundedCornerShape(MakiRadius.pill)).background(fill),
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1100)
@Composable
private fun MakiComponentsPreview() {
    MAKITheme {
        Column(
            Modifier.fillMaxWidth().background(MakiColors.Bg)
                .verticalScroll(rememberScrollState()).padding(MakiSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            HeroCard(MakiColors.Gen, "SALDO DISPONIBLE", "S/ 128.50", "Tu reciclaje suma") {
                ProgressTrack(0.65f)
            }
            HeroCard(MakiColors.Rider, "GANANCIAS DEL MES", "S/ 840.00", "24 recojos completados") {
                ProgressTrack(1.4f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md)) {
                MetricTile(Icons.Filled.Recycling, "32 kg", "Reciclados", MakiColors.Gen, Modifier.weight(1f))
                MetricTile(Icons.Filled.LocalShipping, "24", "Recojos", MakiColors.Rider, Modifier.weight(1f), onClick = {})
            }
            NavRow(Icons.Filled.AccountBalanceWallet, "Billetera", "Consulta tu saldo", trailing = "S/ 128.50", onClick = {})
            NavRow(Icons.Filled.LocalShipping, "Mi ruta", "Tus próximos recojos", accent = MakiColors.Rider, onClick = {})
            NavRow(Icons.Filled.Person, "Perfil", "Disponible próximamente", enabled = false, onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                StatusChip("Generador", MakiColors.Gen)
                StatusChip("Rider", MakiColors.Rider)
                StatusChip("Acopio", MakiColors.Admin)
                StatusChip("Saldo", MakiColors.Money)
            }
        }
    }
}
