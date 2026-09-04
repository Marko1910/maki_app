package com.example.maki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign

/** Round surface button with a back arrow (used on detail screens). */
@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .makiShadow(20.dp, 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MakiColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MakiColors.Text, modifier = Modifier.size(20.dp))
    }
}

/** Top bar with a back button, a title and optional trailing/leading content. */
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
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        if (leadingIcon != null) Icon(leadingIcon, null, tint = leadingIconTint, modifier = Modifier.size(22.dp))
        Text(
            title,
            color = MakiColors.Text,
            fontFamily = MakiFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** Uppercase section label (e.g. "OPCIONES DE CANJE"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MakiColors.Text2,
        fontFamily = MakiFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = modifier,
    )
}

/** White rounded card with the soft Maki shadow. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    padding: Dp = 16.dp,
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .makiShadow(radius, 5.dp)
            .clip(RoundedCornerShape(radius))
            .background(MakiColors.Surface)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * Centred "loading" row for a list that has not resolved yet. Lives inside a
 * LazyColumn `item { }` so every list screen shows the same waiting state.
 */
@Composable
fun MakiLoading(label: String = "Cargando…", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = MakiColors.Rider, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
        Text(
            title,
            color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                hint,
                color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Error state with a retry affordance — a failure the user can act on, not a dead end. */
@Composable
fun MakiError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MakiColors.Text2, modifier = Modifier.size(30.dp))
        Text(
            message,
            color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MakiColors.Rider)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 11.dp),
        ) {
            Text("Reintentar", color = MakiColors.OnDark, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
