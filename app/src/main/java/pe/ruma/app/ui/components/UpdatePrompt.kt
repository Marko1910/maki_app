package pe.ruma.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import pe.ruma.app.data.AppUpdater
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import java.io.File
import java.util.Locale

private sealed interface UpdateStep {
    data object Offer : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data class Failed(val reason: String) : UpdateStep
}

/**
 * Checks GitHub Releases once per launch (data/AppUpdater.kt) and, if there is a newer APK, offers
 * it: download with progress, then Android's installer. "Más tarde" hides it until the next launch.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdater.Update?>(null) }
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Offer) }

    LaunchedEffect(Unit) { update = AppUpdater.check() }
    val u = update ?: return

    fun startDownload() {
        step = UpdateStep.Downloading(0f)
        scope.launch {
            step = runCatching { AppUpdater.download(context, u) { p -> step = UpdateStep.Downloading(p) } }
                .fold({ UpdateStep.Ready(it) }, { UpdateStep.Failed("Revisa tu conexión e inténtalo otra vez.") })
        }
    }

    // Straight to Android's installer, even without "allow from this source": the installer asks for it
    // itself and resumes after. Opening that setting from here lost the download (the toggle restarts Ruma).
    fun install(apk: File) = AppUpdater.install(context, apk)

    val current = step
    AlertDialog(
        onDismissRequest = { if (current !is UpdateStep.Downloading) update = null },
        containerColor = RumaColors.Surface,
        title = { Text("Hay una versión nueva de Ruma", style = RumaType.cardTitle, color = RumaColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.md)) {
                when (current) {
                    UpdateStep.Offer -> {
                        Text(u.notes.ifBlank { u.title }, style = RumaType.body, color = RumaColors.Text2)
                        if (u.sizeBytes > 0) {
                            Text(
                                "Descarga de %.0f MB. Tu cuenta y tus puntos se mantienen.".format(Locale.US, u.sizeBytes / 1_048_576.0),
                                style = RumaType.label, color = RumaColors.Text2,
                            )
                        }
                    }
                    is UpdateStep.Downloading -> {
                        Text("Descargando… ${(current.progress * 100).toInt()} %", style = RumaType.body, color = RumaColors.Text2)
                        ProgressTrack(current.progress, track = RumaColors.GenTint, fill = RumaColors.Gen)
                    }
                    is UpdateStep.Ready -> Text(
                        "Listo. Toca Instalar y confirma en la pantalla de Android. La primera vez te pedirá permitir instalar desde Ruma: actívalo y vuelve atrás.",
                        style = RumaType.body, color = RumaColors.Text2,
                    )
                    is UpdateStep.Failed -> Text(current.reason, style = RumaType.body, color = RumaColors.Error)
                }
            }
        },
        confirmButton = {
            when (current) {
                UpdateStep.Offer, is UpdateStep.Failed -> TextButton(onClick = ::startDownload) {
                    Text(if (current is UpdateStep.Failed) "Reintentar" else "Actualizar", style = RumaType.button, color = RumaColors.Gen)
                }
                is UpdateStep.Ready -> TextButton(onClick = { install(current.apk) }) {
                    Text("Instalar", style = RumaType.button, color = RumaColors.Gen)
                }
                is UpdateStep.Downloading -> Unit
            }
        },
        dismissButton = {
            if (current !is UpdateStep.Downloading) {
                TextButton(onClick = { update = null }) { Text("Más tarde", style = RumaType.button, color = RumaColors.Text2) }
            }
        },
    )
}
