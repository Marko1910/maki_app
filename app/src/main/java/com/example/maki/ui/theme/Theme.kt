package com.example.maki.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MakiLightColors = lightColorScheme(
    primary = MakiColors.Gen,
    onPrimary = MakiColors.OnDark,
    secondary = MakiColors.Rider,
    tertiary = MakiColors.Admin,
    background = MakiColors.Bg,
    onBackground = MakiColors.Text,
    surface = MakiColors.Surface,
    onSurface = MakiColors.Text,
    error = MakiColors.Error,
    outline = MakiColors.Border,
)

@Composable
fun MAKITheme(
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
        colorScheme = MakiLightColors,
        typography = MakiTypography,
        content = content
    )
}
