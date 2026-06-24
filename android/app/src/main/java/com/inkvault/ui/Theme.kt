package com.inkvault.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.inkvault.export.ThemeMode
import com.inkvault.ui.theme.InkDarkColors
import com.inkvault.ui.theme.InkLightColors
import com.inkvault.ui.theme.InkShapes
import com.inkvault.ui.theme.InkTypography

/**
 * The "Ink & Ncode" theme — Material 3 *Expressive* with the brand [InkLightColors]/[InkDarkColors],
 * [InkTypography], and [InkShapes]. Honors the user's [ThemeMode] (system / forced light / dark).
 *
 * [MaterialExpressiveTheme] brings the Expressive spring/physics motion scheme app-wide (livelier
 * component transitions) while we keep our own brand color/shape/type. Material You dynamic color is
 * intentionally NOT used: the brand identity (teal ink on paper, brass accents) must hold regardless
 * of device wallpaper (design-system §12).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkVaultTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Keep the OS status/nav-bar glyphs (clock, gesture pill) legible against OUR background:
    // dark theme → light icons, light theme → dark icons. enableEdgeToEdge() keys off the *system*
    // dark-mode setting, which can disagree with the in-app theme override — so set it explicitly.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = if (dark) InkDarkColors else InkLightColors,
        typography = InkTypography,
        shapes = InkShapes,
        content = content,
    )
}
