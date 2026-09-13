package com.sanka1610.reprodroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.local.ThemeMode

private val ReproDroidDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFCEBDFF),
    onPrimary = Color(0xFF352267),
    primaryContainer = Color(0xFF4C397F),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF938F99),
    error = Color(0xFFFFB4AB),
)

private val ReproDroidLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF4D3D75),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFF7FF),
    surface = Color(0xFFFFF7FF),
    surfaceVariant = Color(0xFFE7E0EC),
    outline = Color(0xFF79747E),
    error = Color(0xFFBA1A1A),
)

val LocalSelectionBoxOutlines = staticCompositionLocalOf { true }

@Composable
fun ReproDroidTheme(
    settings: GlobalSettingsEntity,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val themeMode = settings.themeMode.asThemeMode()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.PURE_BLACK -> true
    }
    val colorScheme = remember(context, darkTheme, themeMode, settings.dynamicColorEnabled) {
        val base = when {
            settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
                dynamicDarkColorScheme(context)
            settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(context)
            darkTheme -> ReproDroidDarkColors
            else -> ReproDroidLightColors
        }
        if (themeMode == ThemeMode.PURE_BLACK) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF171717),
                surfaceTint = Color.Transparent,
            )
        } else {
            base
        }
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalSelectionBoxOutlines provides settings.showSelectionBoxOutlines) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

private fun String.asThemeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM
