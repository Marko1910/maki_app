package com.example.maki

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.maki.data.MakiLocation
import com.example.maki.data.RoutePlanner
import com.example.maki.data.StreetRouter
import com.example.maki.ui.screens.rider.RiderNavigationScreen
import com.example.maki.ui.screens.rider.RiderNavigationUiState
import com.example.maki.ui.theme.MAKITheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs the real screen on hardware, with no repository or pickup writes. */
@RunWith(AndroidJUnit4::class)
class RiderNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val origin = RoutePlanner.LatLng(-12.057, -77.034)
    private val destination = RoutePlanner.LatLng(-12.046, -77.043)
    private val state = RiderNavigationUiState(
        stopName = "Destino de prueba", address = "Centro de Lima · prueba sin recojo",
        distance = "2.8 km", eta = "~9 min", instruction = "Datos simulados para comprobar la navegación",
        lat = destination.lat, lng = destination.lng,
        path = listOf(origin, RoutePlanner.LatLng(-12.055, -77.036), destination),
        roadKm = 2.8, roadMinutes = 9,
        maneuver = "Gira a la derecha por Jirón de la Unión", maneuverMeters = 400,
        maneuverKey = "test-turn",
    )

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("navigation-qa")!!
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Device screenshot is available", bitmap)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun mapAndControlsAreUsable() {
        var arrived = false
        compose.setContent { MAKITheme {
            RiderNavigationScreen(state = state, onArrived = { arrived = true },
                positionSource = { MakiLocation.NavigationFix(origin, 10f, System.currentTimeMillis()) })
        } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Gira a la derecha por Jirón de la Unión").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Centrar").assertIsEnabled().performClick()
        screenshot("android-route")
        compose.onNodeWithText("He llegado al punto").performScrollTo().assertIsDisplayed().performClick()
        assertTrue("Only the local callback was invoked", arrived)
        compose.onNodeWithText("Activar voz").performScrollTo().performClick()
        compose.onNodeWithText("Silenciar guía").assertIsDisplayed().performClick()
    }

    @Test fun missingGpsDoesNotPretendToHaveLocation() {
        compose.setContent { MAKITheme {
            RiderNavigationScreen(state = state, positionSource = { null })
        } }
        compose.onNodeWithText("Esperando ubicación GPS").assertIsDisplayed()
        compose.onNodeWithText("Centrar").assertIsNotEnabled()
        screenshot("android-no-gps")
    }

    @Test fun failedRouteCanBeRetriedWithoutMoving() {
        var retries = 0
        compose.setContent { MAKITheme {
            RiderNavigationScreen(state = state.copy(path = emptyList(), roadKm = null, roadMinutes = null,
                maneuver = null, maneuverMeters = null, roadError = "No se pudo actualizar la ruta."),
                onRetry = { retries++ },
                positionSource = { MakiLocation.NavigationFix(origin, 10f, System.currentTimeMillis()) })
        } }
        compose.onNodeWithText("Reintentar ruta").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retries)
        screenshot("android-route-error")
    }

    @Test fun realRoadProviderRespondsOnDeviceNetwork() = runBlocking {
        val road = StreetRouter.route(listOf(origin, destination), "moto")
        assertNotNull("OSRM responds over the device's actual network", road)
        assertTrue(road!!.path.size > 2)
        assertTrue(road.steps.isNotEmpty())
        assertTrue(road.distanceKm > 0)
    }
}
