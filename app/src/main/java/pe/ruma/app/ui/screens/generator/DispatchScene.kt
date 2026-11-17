package pe.ruma.app.ui.screens.generator

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pe.ruma.app.R
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.theme.RumaRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import org.osmdroid.util.GeoPoint
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Pre-rendered 3D frames (Blender: landing/3d/ruma-fardo-v2.blend and the Eco-Rider tricycle), the PWA's public/anim.
private val BaleFrames = listOf(
    R.drawable.anim_bale_00, R.drawable.anim_bale_01, R.drawable.anim_bale_02, R.drawable.anim_bale_03,
    R.drawable.anim_bale_04, R.drawable.anim_bale_05, R.drawable.anim_bale_06, R.drawable.anim_bale_07,
    R.drawable.anim_bale_08, R.drawable.anim_bale_09, R.drawable.anim_bale_10, R.drawable.anim_bale_11,
    R.drawable.anim_bale_12, R.drawable.anim_bale_13, R.drawable.anim_bale_14, R.drawable.anim_bale_15,
    R.drawable.anim_bale_16, R.drawable.anim_bale_17,
)
private val RiderFrames = listOf(
    R.drawable.anim_rider_00, R.drawable.anim_rider_01, R.drawable.anim_rider_02, R.drawable.anim_rider_03,
    R.drawable.anim_rider_04, R.drawable.anim_rider_05, R.drawable.anim_rider_06, R.drawable.anim_rider_07,
    R.drawable.anim_rider_08, R.drawable.anim_rider_09, R.drawable.anim_rider_10, R.drawable.anim_rider_11,
)

private val CityFrames = listOf(R.drawable.anim_city_00, R.drawable.anim_city_01, R.drawable.anim_city_02, R.drawable.anim_city_03, R.drawable.anim_city_04, R.drawable.anim_city_05, R.drawable.anim_city_06, R.drawable.anim_city_07, R.drawable.anim_city_08, R.drawable.anim_city_09, R.drawable.anim_city_10, R.drawable.anim_city_11, R.drawable.anim_city_12, R.drawable.anim_city_13, R.drawable.anim_city_14, R.drawable.anim_city_15, R.drawable.anim_city_16, R.drawable.anim_city_17, R.drawable.anim_city_18, R.drawable.anim_city_19, R.drawable.anim_city_20, R.drawable.anim_city_21, R.drawable.anim_city_22, R.drawable.anim_city_23)
private val PinFrames = listOf(R.drawable.anim_pin_00, R.drawable.anim_pin_01, R.drawable.anim_pin_02, R.drawable.anim_pin_03, R.drawable.anim_pin_04, R.drawable.anim_pin_05, R.drawable.anim_pin_06, R.drawable.anim_pin_07, R.drawable.anim_pin_08, R.drawable.anim_pin_09, R.drawable.anim_pin_10, R.drawable.anim_pin_11, R.drawable.anim_pin_12, R.drawable.anim_pin_13, R.drawable.anim_pin_14, R.drawable.anim_pin_15, R.drawable.anim_pin_16, R.drawable.anim_pin_17, R.drawable.anim_pin_18, R.drawable.anim_pin_19, R.drawable.anim_pin_20, R.drawable.anim_pin_21, R.drawable.anim_pin_22, R.drawable.anim_pin_23)

/** "Remove animations" in the system settings sets the animator scale to 0: then every scene stays still. */
@Composable
private fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Cycles a frame sequence decoded once (a fresh decode per frame stutters on low-end phones). */
@Composable
private fun Sprite(ids: List<Int>, fps: Int, playing: Boolean, modifier: Modifier) {
    val res = LocalContext.current.resources
    val frames: List<ImageBitmap> = remember(ids) { ids.map { ImageBitmap.imageResource(res, it) } }
    var i by remember { mutableIntStateOf(0) }
    val still = reducedMotion()
    LaunchedEffect(playing, still) {
        while (playing && !still) { delay(1000L / fps); i = (i + 1) % frames.size }
    }
    Image(frames[i], null, modifier)
}

/**
 * The dispatch moment in 3D, twin of the PWA's DispatchScene: while nobody has taken the pickup the bale
 * turns over pulsing rings; once a rider accepts they ride in on the cargo tricycle and stop.
 * Wrap it in key(riderName) so a new acceptance replays the arrival.
 */
@Composable
fun DispatchScene(riderName: String?, modifier: Modifier = Modifier, at: RoutePlanner.LatLng? = null) {
    SoftCard(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, gap = RumaSpace.xs) {
        if (riderName == null) Searching(at) else Arriving(riderName)
    }
}

/**
 * A map centred on [at] with the spinning 3D Ruma pin standing on that point (twin of the PWA's PinMap);
 * [radar] adds the pulse a ride app shows while it looks for a driver.
 */
@Composable
fun PinMap(at: RoutePlanner.LatLng, modifier: Modifier = Modifier, radar: Boolean = false) {
    val still = reducedMotion()
    val pulse = rememberInfiniteTransition(label = "radar")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Box(modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(RumaRadius.card)), contentAlignment = Alignment.Center) {
        OsmMap(center = GeoPoint(at.lat, at.lng), zoom = 16.0, modifier = Modifier.fillMaxSize())
        if (radar) Canvas(Modifier.fillMaxSize()) {
            for (k in 0..2) {
                val p = if (still) .5f else (t + k / 3f) % 1f
                val r = 12.dp.toPx() + 90.dp.toPx() * p
                drawCircle(RumaColors.Gen.copy(alpha = (if (still) .18f else .3f * (1 - p))), r, center)
                drawCircle(RumaColors.Gen.copy(alpha = (if (still) .3f else .55f * (1 - p))), r, center, style = Stroke(2.dp.toPx()))
            }
        }
        // The pin's tip sits on the point: the frame's lower part is the tip and its shadow.
        Sprite(PinFrames, fps = 16, playing = true, modifier = Modifier.width(76.dp).offset(y = (-32).dp))
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun Searching(at: RoutePlanner.LatLng?) {
    // A 3D mini city (Blender): the house under the Ruma pin pulses like a radar while an Eco-Rider circles the block.
    Sprite(CityFrames, fps = 12, playing = true, modifier = Modifier.fillMaxWidth().height(230.dp))
    Text("Buscando al Eco-Rider más cercano…", Modifier.fillMaxWidth(), style = RumaType.rowTitle, color = RumaColors.Text, textAlign = TextAlign.Center)
    Text("Tu pedido ya está en el tablero de los Eco-Riders de tu zona.", Modifier.fillMaxWidth(), style = RumaType.bodySmall, color = RumaColors.Text2, textAlign = TextAlign.Center)
    return
    val still = reducedMotion()
    val pulse = rememberInfiniteTransition(label = "rings")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(168.dp).height(150.dp)) {
            // Three rings a third of a cycle apart, flattened to sit on the floor under the bale.
            for (k in 0..2) {
                val p = if (still) .5f else (t + k / 3f) % 1f
                val w = size.width * (.4f + .55f * p)
                val h = w * .38f
                drawOval(
                    RumaColors.Gen.copy(alpha = if (still) .2f else .5f * (1 - p)),
                    topLeft = Offset((size.width - w) / 2, size.height * .62f - h / 2),
                    size = Size(w, h), style = Stroke(2.dp.toPx()),
                )
            }
        }
        Sprite(BaleFrames, fps = 12, playing = true, modifier = Modifier.width(132.dp))
    }
    Text("Buscando al Eco-Rider más cercano…", Modifier.fillMaxWidth(), style = RumaType.rowTitle, color = RumaColors.Text, textAlign = TextAlign.Center)
    Text("Tu pedido ya está en el tablero de los Eco-Riders de tu zona.", Modifier.fillMaxWidth(), style = RumaType.bodySmall, color = RumaColors.Text2, textAlign = TextAlign.Center)
}

@Composable
private fun Arriving(riderName: String) {
    val still = reducedMotion()
    val x = remember { Animatable(if (still) 0f else -1.4f) }
    var arrived by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) {
        if (!still) {
            x.animateTo(.04f, tween(1550, easing = FastOutSlowInEasing))
            x.animateTo(0f, tween(350))
        }
        arrived = true
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        val riderWidth = 190.dp
        val px = with(density) { riderWidth.toPx() }
        Sprite(
            RiderFrames, fps = 14, playing = !arrived,
            modifier = Modifier.width(riderWidth)
                .offset { IntOffset((x.value * px).roundToInt(), 0) }
                // A small brake rock while it settles.
                .graphicsLayer { rotationZ = if (x.value > 0f) -30f * x.value else 0f },
        )
        if (arrived) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(x = riderWidth / 2 + 8.dp, y = 6.dp)
                    .size(30.dp).clip(CircleShape).background(RumaColors.Gen),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = RumaColors.OnDark, modifier = Modifier.size(RumaSpace.lg)) }
        }
    }
    Text("¡$riderName aceptó tu recojo!", Modifier.fillMaxWidth(), style = RumaType.rowTitle, color = RumaColors.Text, textAlign = TextAlign.Center)
    Text("Vendrá en su triciclo dentro de tu ventana horaria.", Modifier.fillMaxWidth(), style = RumaType.bodySmall, color = RumaColors.Text2, textAlign = TextAlign.Center)
}
