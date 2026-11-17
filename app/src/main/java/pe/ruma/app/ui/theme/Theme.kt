package pe.ruma.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RumaLightColors = lightColorScheme(
    primary = RumaColors.Gen,
    onPrimary = RumaColors.OnDark,
    secondary = RumaColors.Rider,
    tertiary = RumaColors.Admin,
    background = RumaColors.Bg,
    onBackground = RumaColors.Text,
    surface = RumaColors.Surface,
    onSurface = RumaColors.Text,
    error = RumaColors.Error,
    outline = RumaColors.Border,
)

@Composable
fun RumaTheme(
    // The app design is a single light theme; dark mode is intentionally off.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = RumaLightColors,
        typography = RumaTypography,
        content = content
    )
}
