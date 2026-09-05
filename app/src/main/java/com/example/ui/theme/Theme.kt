package com.example.ui.theme

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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- INDIGO THEME ---
private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFFF7F2FA),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0),
    outlineVariant = Color(0xFFE7E0EC)
)

private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// --- EMERALD THEME ---
private val EmeraldLightColorScheme = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF6CD),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF3F6375),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC3E8FD),
    background = Color(0xFFF2F8F5),
    onBackground = Color(0xFF161D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C3)
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6FDBB2),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFF8CF6CD),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA6CCE1),
    onTertiary = Color(0xFF0B3545),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE0E3E0),
    surface = Color(0xFF171E1B),
    onSurface = Color(0xFFE0E3E0),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4944)
)

// --- AMBER THEME ---
private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF944B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC7),
    onPrimaryContainer = Color(0xFF311300),
    secondary = Color(0xFF755846),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC8),
    onSecondaryContainer = Color(0xFF2B1709),
    tertiary = Color(0xFF665E2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE3A8),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DFD4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF84746B),
    outlineVariant = Color(0xFFD7C2B8)
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF502400),
    primaryContainer = Color(0xFF713700),
    onPrimaryContainer = Color(0xFFFFDCC7),
    secondary = Color(0xFFE5BEAA),
    onSecondary = Color(0xFF422B1B),
    secondaryContainer = Color(0xFF5B4130),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFFD2C78E),
    onTertiary = Color(0xFF373005),
    background = Color(0xFF19120E),
    onBackground = Color(0xFFEFE0D9),
    surface = Color(0xFF211A16),
    onSurface = Color(0xFFEFE0D9),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8E84),
    outlineVariant = Color(0xFF52443C)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePreset: AppThemePreset = AppThemePreset.INDIGO,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset == AppThemePreset.EMERALD -> {
            if (darkTheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
        }
        themePreset == AppThemePreset.AMBER -> {
            if (darkTheme) AmberDarkColorScheme else AmberLightColorScheme
        }
        else -> {
            if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
