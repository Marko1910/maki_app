package pe.ruma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

// The PWA's list and chip vocabulary (pwa/src/styles.css). A screen that needs a
// grouped list, a filter or a small action takes it from here, so Android and the
// PWA keep one look instead of each screen drawing its own approximation.

/**
 * A grouped list: one glass card, its rows split by hairlines (PWA `.glass.list`).
 * Ten separate cards for ten rows is ten frames; one card is one list.
 */
@Composable
fun <T> RumaList(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth().rumaCard()) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
            row(item)
        }
    }
}

/**
 * One row of a [RumaList] (PWA `.list > *`): an optional badge, a title, its line,
 * and whatever the row ends with. A chevron appears only when the row opens something.
 */
@Composable
fun RumaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Marks the row that is "you" or still unread, with the role's tint. */
    highlight: Color? = null,
    /** More lines under the subtitle: a status chip, a time. */
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    /** A quiet note on the title's line, at its end: "Hace 2 h". */
    meta: String? = null,
    /** Off for a row whose tap toggles it (a checkbox) instead of opening something. */
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (highlight != null) Modifier.background(highlight.copy(alpha = 0.12f)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Abrir $title", role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = RumaSpace.lg, vertical = RumaSpace.md),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2)) {
            if (meta == null) Text(title, color = RumaColors.Text, style = RumaType.rowTitle)
            else Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                Text(title, color = RumaColors.Text, style = RumaType.rowTitle, modifier = Modifier.weight(1f))
                Text(meta, color = RumaColors.Text2, style = RumaType.caption)
            }
            if (subtitle != null) Text(subtitle, color = RumaColors.Text2, style = RumaType.bodySmall)
            extra?.invoke(this)
        }
        trailing?.invoke()
        if (onClick != null && chevron) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = RumaColors.Text2, modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The neutral square every list row leads with (PWA `.badge`): glass, ink icon or two
 * letters. [fill] and [content] turn it into a numbered stop or a medal.
 */
@Composable
fun GlassBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    size: Dp = 40.dp,
    circle: Boolean = false,
    fill: Color = RumaColors.SurfaceGlass,
    content: Color = RumaColors.Text,
    ring: Color = RumaColors.Hairline,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(RumaRadius.badge - RumaSpace.xs / 2)
    Box(
        modifier.size(size).clip(shape).background(fill).border(1.dp, ring, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(size / 2))
        if (text != null) Text(text, color = content, style = RumaType.chip, maxLines = 1)
    }
}

/** A filter that stays on (PWA `.filter`): glass pill, the role tint when chosen. */
@Composable
fun FilterPill(label: String, selected: Boolean, accent: Color = RumaColors.Gen, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(RumaRadius.pill))
            .background(if (selected) accent.copy(alpha = 0.12f) else RumaColors.SurfaceGlass)
            .then(if (selected) Modifier else Modifier.border(1.dp, RumaColors.Hairline, RoundedCornerShape(RumaRadius.pill)))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = RumaSpace.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) accent else RumaColors.Text, style = RumaType.button)
    }
}

/** The action at the end of a row (PWA `.btn-small`): filled for the first, outlined after. */
@Composable
fun SmallButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    color: Color = RumaColors.Gen,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RumaRadius.badge - RumaSpace.xs / 2)
    Box(
        modifier.alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .then(
                if (filled) Modifier.background(RumaGradient.button(color))
                else Modifier.background(RumaColors.SurfaceGlass).border(1.dp, color.copy(alpha = 0.35f), shape)
            )
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = RumaSpace.md + RumaSpace.xs / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) RumaColors.OnDark else color, style = RumaType.chip)
    }
}

/** The plain chip (PWA `.chip`): glass and ink, for a fact that is not a status. */
@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, dot: Color? = null, color: Color = RumaColors.Text) {
    Row(
        modifier.clip(RoundedCornerShape(RumaRadius.pill))
            .background(RumaColors.SurfaceGlass)
            .border(1.dp, RumaColors.Hairline, RoundedCornerShape(RumaRadius.pill))
            .padding(horizontal = RumaSpace.sm + RumaSpace.xs / 2, vertical = RumaSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs + RumaSpace.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(text, color = color, style = RumaType.chip)
    }
}

/** A chip on a gradient hero (PWA `.chip-hero`). */
@Composable
fun HeroChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(RumaRadius.pill))
            .background(RumaColors.WhiteTint)
            .border(1.dp, RumaColors.OnGradientTrack, RoundedCornerShape(RumaRadius.pill))
            .padding(horizontal = RumaSpace.sm + RumaSpace.xs / 2, vertical = RumaSpace.xs),
        color = RumaColors.OnDark,
        style = RumaType.chip,
        maxLines = 1,
    )
}

/** The hairline that splits a hero's figure from its detail (PWA `.hero-rule`). */
@Composable
fun HeroRule(modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = RumaSpace.xs).fillMaxWidth().height(1.dp).background(RumaColors.OnGradientTrack))
}

/** A figure and its name in a glass tile, no icon (PWA `Metric`). */
@Composable
fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().rumaCard().padding(RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2),
    ) {
        Text(value, color = RumaColors.Text, style = RumaType.metricMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(label, color = RumaColors.Text2, style = RumaType.bodySmall, maxLines = 2)
    }
}

/**
 * A radio card (PWA `.choice`): the chosen one gets the role's border and a filled
 * ring. Picking is separate from going, so a wrong tap costs nothing.
 */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = RumaColors.Gen,
    leading: (@Composable () -> Unit)? = null,
    /** Sits before the radio: an "Abierto" chip. */
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().rumaCard()
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(RumaRadius.card)) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(RumaSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2)) {
            Text(title, color = RumaColors.Text, style = RumaType.rowTitle)
            if (subtitle != null) Text(subtitle, color = RumaColors.Text2, style = RumaType.bodySmall)
        }
        trailing?.invoke()
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(if (selected) 6.dp else 2.dp, if (selected) accent else RumaColors.Text2, CircleShape),
        )
    }
}

/** One bar of [RumaBars]: [value] sets the height, [text] is what it says. */
data class RumaBar(val label: String, val value: Float, val text: String, val today: Boolean = false)

/**
 * The week as bars (PWA `Bars`): the figure on top, the day under, today at full
 * strength and the rest faded. One node for a screen reader, not seven mute boxes.
 */
@Composable
fun RumaBars(bars: List<RumaBar>, accent: Color, modifier: Modifier = Modifier, unit: String = "") {
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Row(
        modifier.fillMaxWidth().height(150.dp)
            .clearAndSetSemantics { contentDescription = bars.joinToString(", ") { "${it.label}: ${it.text}$unit" } },
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RumaSpace.xs, Alignment.Bottom),
            ) {
                Text(b.text, color = RumaColors.Text, style = RumaType.chip, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 3.dp).fillMaxHeight(0.7f * (b.value / max).coerceIn(0f, 1f))
                        .alpha(if (b.today) 1f else 0.6f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(RumaGradient.button(accent)),
                )
                Text(b.label, color = RumaColors.Text2, style = RumaType.caption, maxLines = 1)
            }
        }
    }
}

/** A header's text action (PWA `.btn-text`): "Marcar leídas", "Saltar". 48 dp tall. */
@Composable
fun TextAction(label: String, color: Color = RumaColors.Gen, onClickLabel: String = label, onClick: () -> Unit) {
    Text(
        label, color = color, style = RumaType.button,
        modifier = Modifier.clip(RoundedCornerShape(RumaRadius.chip))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = RumaSpace.sm),
    )
}

/**
 * Where you stand on a leaderboard (PWA `Leaderboard` hero): your place, your name,
 * how far the leader is, and your points. Shared by both roles' rankings.
 */
@Composable
fun RankHero(rank: Int, name: String, gap: String?, points: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().rumaHero(RumaGradient.forAccent(accent), glow = RumaGradient.glowOf(accent))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text("TU POSICIÓN", color = RumaGradient.softOn(accent), style = RumaType.overline)
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Text("#$rank", color = RumaColors.OnDark, style = RumaType.metricHero)
            Text(name, color = RumaColors.OnDark, style = RumaType.sectionHeading, modifier = Modifier.weight(1f))
        }
        HeroRule()
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            HeroChip(gap ?: "Vas primero")
            Box(Modifier.weight(1f))
            Text("$points pts", color = RumaColors.OnDark, style = RumaType.rowTitle)
        }
    }
}

/** A leaderboard row: medal for the podium, initials, name, streak, points. */
@Composable
fun RankRow(
    rank: Int,
    initials: String,
    name: String,
    points: String,
    accent: Color,
    isYou: Boolean = false,
    sub: String? = null,
) {
    val medal = when (rank) { 1 -> RumaColors.Gold; 2 -> RumaColors.Silver; 3 -> RumaColors.Bronze; else -> null }
    RumaRow(
        name,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Puesto $rank, $name" + (if (isYou) ", tú" else "") + ", $points puntos" + (sub?.let { ". $it" } ?: "")
        },
        subtitle = sub,
        highlight = if (isYou) accent else null,
        leading = {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                if (medal != null) GlassBadge(text = "$rank", size = 32.dp, circle = true, fill = medal, content = RumaColors.OnDark, ring = medal)
                else GlassBadge(text = "$rank", size = 32.dp, circle = true, content = RumaColors.Text2)
                GlassBadge(text = initials, circle = true)
            }
        },
        extra = if (isYou) { { StatusChip("Tú", accent) } } else null,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs), verticalAlignment = Alignment.Bottom) {
                Text(points, color = RumaColors.Text, style = RumaType.rowTitle)
                Text("pts", color = RumaColors.Text2, style = RumaType.caption)
            }
        },
    )
}

/** "Marco Nureña" → "MN": the avatar until photos exist (PWA `initials`). */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }.ifBlank { "·" }

/** "¿No tienes cuenta? Regístrate": a muted question and its accent action on one line. */
@Composable
fun InlineLink(question: String, action: String, color: Color = RumaColors.Gen, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(question, color = RumaColors.Text2, style = RumaType.body)
        TextAction(action, color, onClick = onClick)
    }
}

/**
 * A counter with a fixed width (PWA `.stepper`): − and + as 48 dp targets around the
 * figure, so a long material name wraps instead of pushing the buttons around.
 */
@Composable
fun RumaStepper(value: Int, label: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RumaRadius.badge - RumaSpace.xs / 2)
    Row(
        modifier.clip(shape).background(RumaColors.SurfaceField).border(1.dp, RumaColors.Hair, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", "Quitar uno de $label", enabled = value > 0, onClick = onMinus)
        Text(
            "$value", color = RumaColors.Text, style = RumaType.rowTitle,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.size(width = 28.dp, height = 48.dp).wrapContentHeight(),
        )
        StepperButton("+", "Sumar uno a $label", enabled = true, onClick = onPlus)
    }
}

@Composable
private fun StepperButton(sign: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(sign, color = RumaColors.Text, style = RumaType.sectionHeading) }
}
