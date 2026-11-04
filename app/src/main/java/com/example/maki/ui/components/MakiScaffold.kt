package com.example.maki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiElevation
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .makiShadow(MakiRadius.pill, MakiElevation.card)
            .size(48.dp)
            .clip(CircleShape)
            .background(MakiColors.Surface)
            .clickable(onClickLabel = "Volver", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MakiColors.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: androidx.compose.ui.graphics.Color = MakiColors.Money,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        if (leadingIcon != null) Icon(leadingIcon, null, tint = leadingIconTint, modifier = Modifier.size(22.dp))
        Text(
            title,
            color = MakiColors.Text,
            style = MakiType.screenTitle,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(java.util.Locale.ROOT),
        color = MakiColors.Text2,
        style = MakiType.overline,
        modifier = modifier,
    )
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = MakiRadius.card,
    padding: Dp = MakiSpace.lg,
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .makiCard(radius)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * Sharing a waiting state keeps unresolved lists from appearing empty or broken.
 */
@Composable
fun MakiLoading(
    label: String = "Cargando…",
    modifier: Modifier = Modifier,
    accent: Color = MakiColors.Gen,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = MakiSpace.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        // A fixed rider accent put a blue spinner on generator screens.
        CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        Text(label, color = MakiColors.Text2, style = MakiType.bodySmall)
    }
}

/**
 * Empty state: an icon, what is missing and what to do about it. A blank screen
 * reads as a bug, so every list falls back to this instead of nothing.
 */
@Composable
fun MakiEmpty(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MakiColors.Rider,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
        Text(
            title,
            color = MakiColors.Text, style = MakiType.cardTitle,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                hint,
                color = MakiColors.Text2, style = MakiType.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Error state with a retry affordance — a failure the user can act on, not a dead end. */
@Composable
fun MakiError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MakiColors.Gen,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.lg * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MakiColors.Text2, modifier = Modifier.size(30.dp))
        Text(
            message,
            color = MakiColors.Text2, style = MakiType.bodySmall,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.clip(RoundedCornerShape(MakiRadius.badge)).background(accent)
                .clickable(onClickLabel = "Reintentar", role = Role.Button, onClick = onRetry)
                .heightIn(min = 48.dp)
                .padding(horizontal = MakiSpace.xl, vertical = MakiSpace.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("Reintentar", color = MakiColors.OnDark, style = MakiType.button)
        }
    }
}
