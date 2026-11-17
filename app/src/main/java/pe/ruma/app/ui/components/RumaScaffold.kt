package pe.ruma.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaElevation
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .rumaShadow(RumaRadius.pill, RumaElevation.card)
            .size(48.dp)
            .clip(CircleShape)
            // Glass like the PWA's `.icon-btn`: a solid white disc read as a sticker.
            .background(RumaColors.SurfaceGlass)
            .border(1.dp, RumaColors.Hairline, CircleShape)
            .clickable(onClickLabel = "Volver", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = RumaColors.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: androidx.compose.ui.graphics.Color = RumaColors.Money,
    /** The line under the title (PWA `TopBar` sub): a period, a count. */
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        if (leadingIcon != null) Icon(leadingIcon, null, tint = leadingIconTint, modifier = Modifier.size(22.dp))
        // A pushed screen titles at card size, like the PWA's TopBar with a back
        // button; the 26 sp title belongs to the tabs (see [ScreenHeader]).
        Column(Modifier.weight(1f)) {
            Text(title, color = RumaColors.Text, style = RumaType.sectionHeading)
            if (subtitle != null) Text(subtitle, color = RumaColors.Text2, style = RumaType.bodySmall)
        }
        if (trailing != null) trailing()
    }
}

/** A tab's title, with no back button (PWA `TopBar back={false}`). */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2)) {
            Text(title, color = RumaColors.Text, style = RumaType.screenTitle)
            if (subtitle != null) Text(subtitle, color = RumaColors.Text2, style = RumaType.bodySmall)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(java.util.Locale.ROOT),
        color = RumaColors.Text2,
        style = RumaType.overline,
        modifier = modifier,
    )
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = RumaRadius.card,
    padding: Dp = RumaSpace.lg,
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .rumaCard(radius)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * Sharing a waiting state keeps unresolved lists from appearing empty or broken.
 */
@Composable
fun RumaLoading(
    label: String = "Cargando…",
    modifier: Modifier = Modifier,
    accent: Color = RumaColors.Gen,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = RumaSpace.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.md),
    ) {
        // A fixed rider accent put a blue spinner on generator screens.
        CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        Text(label, color = RumaColors.Text2, style = RumaType.bodySmall)
    }
}

/**
 * Empty state: an icon, what is missing and what to do about it. A blank screen
 * reads as a bug, so every list falls back to this instead of nothing.
 */
@Composable
fun RumaEmpty(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = RumaColors.Rider,
    /** The one step that fills this list; without it the state only explains. */
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.md),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
        Text(
            title,
            color = RumaColors.Text, style = RumaType.cardTitle,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                hint,
                color = RumaColors.Text2, style = RumaType.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null) {
            Box(
                Modifier.clip(RoundedCornerShape(RumaRadius.badge)).background(accent)
                    .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onAction)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = RumaSpace.xl, vertical = RumaSpace.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, color = RumaColors.OnDark, style = RumaType.button)
            }
        }
    }
}

/**
 * Placeholder blocks in the shape of what is loading, so the screen does not jump when
 * the data lands. One height per block; the pulse stops by itself when the system has
 * animations turned off.
 */
@Composable
fun RumaSkeleton(heights: List<Dp>, modifier: Modifier = Modifier, label: String = "Cargando…") {
    val pulse by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "skeleton")
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        heights.forEach {
            Box(
                Modifier.fillMaxWidth().height(it).graphicsLayer { alpha = pulse }
                    .clip(RoundedCornerShape(RumaRadius.card)).background(RumaColors.SurfaceGlass),
            )
        }
    }
}

/** Error state with a retry affordance — a failure the user can act on, not a dead end. */
@Composable
fun RumaError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = RumaColors.Gen,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.lg * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.md),
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = RumaColors.Text2, modifier = Modifier.size(30.dp))
        Text(
            message,
            color = RumaColors.Text2, style = RumaType.bodySmall,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.clip(RoundedCornerShape(RumaRadius.badge)).background(accent)
                .clickable(onClickLabel = "Reintentar", role = Role.Button, onClick = onRetry)
                .heightIn(min = 48.dp)
                .padding(horizontal = RumaSpace.xl, vertical = RumaSpace.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("Reintentar", color = RumaColors.OnDark, style = RumaType.button)
        }
    }
}

/**
 * A screen's main action, pinned under its scrolling list and kept above the system
 * navigation bar. At the end of a list the button could sit half under the bar on a
 * 720x1600 phone; pinned, it is always whole and always in reach.
 */
@Composable
fun RumaBottomAction(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(RumaColors.SurfaceGlass)
            // Zero when the Root already consumed the inset; the inset when it did not.
            .navigationBarsPadding()
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.md),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
        content = content,
    )
}
