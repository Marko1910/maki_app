package com.example.maki

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.maki.data.MakiLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in hardware checks: requires foreground location permission and speaks one sentence. */
@RunWith(AndroidJUnit4::class)
class RiderHardwareServicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun freshLocationFromDevice() = runBlocking {
        compose.setContent { Text("Comprobando el GPS de Ruma…") }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Enable foreground location permission in Ruma settings", MakiLocation.hasPermission(context))
        val fix = MakiLocation.navigationFix(context)
        assertNotNull("A fresh hardware location must be available", fix)
        assertTrue("Location must be recent", System.currentTimeMillis() - fix!!.time <= 15_000)
        assertTrue("Location must be precise enough to guide", fix.accuracyMeters <= 60)
    }

    @Test fun spanishVoiceCompletesUtterance() {
        compose.setContent { Text("Prueba de voz de Ruma") }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val initialized = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val initStatus = AtomicInteger(-1)
        val utteranceStatus = AtomicInteger(-1)
        lateinit var engine: TextToSpeech
        instrumentation.runOnMainSync {
            engine = TextToSpeech(instrumentation.targetContext) {
                initStatus.set(it)
                initialized.countDown()
            }
        }
        try {
            assertTrue("TTS initializes", initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initStatus.get())
            assertTrue("Spanish voice is installed", engine.setLanguage(Locale.forLanguageTag("es-PE")) >= TextToSpeech.LANG_AVAILABLE)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { utteranceStatus.set(0); completed.countDown() }
                @Deprecated("Required by Android TTS")
                override fun onError(id: String?) { utteranceStatus.set(1); completed.countDown() }
            })
            assertEquals(TextToSpeech.SUCCESS, engine.speak("Prueba de navegación de Ruma.", TextToSpeech.QUEUE_FLUSH, null, "ruma-hardware-test"))
            assertTrue("Speech completes", completed.await(20, TimeUnit.SECONDS))
            assertEquals("Speech completes without engine error", 0, utteranceStatus.get())
        } finally { engine.stop(); engine.shutdown() }
    }
}
