package com.kairos.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = SoftClaySoft,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDF6),
    onSecondaryContainer = Color(0xFF2A1B3D),
    tertiary = QuadrantSchedule,
    onTertiary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = Color(0xFFCBB9E4),
    onSecondary = Color(0xFF33244A),
    tertiary = Color(0xFF8FD3CE),
    onTertiary = Color(0xFF00382F),
    // Dark-first : base chaude + surfaces tonales échelonnées (profondeur par la teinte, pas l'ombre).
    background = Color(0xFF14110E),
    onBackground = Color(0xFFEDE4DC),
    surface = Color(0xFF14110E),
    onSurface = Color(0xFFEDE4DC),
    surfaceVariant = Color(0xFF4E453E),
    onSurfaceVariant = Color(0xFFD2C4B8),
    surfaceContainerLowest = Color(0xFF0F0C0A),
    surfaceContainerLow = Color(0xFF1C1815),
    surfaceContainer = Color(0xFF201B17),
    surfaceContainerHigh = Color(0xFF2B251F),
    surfaceContainerHighest = Color(0xFF362E27),
)

/** Material You disponible uniquement à partir d'Android 12 (API 31). */
val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Thème de marque Kairós, basé sur **Material 3 Expressive**.
 *
 * On conserve la palette « Douce » de marque ; l'utilisateur peut réactiver les couleurs
 * dynamiques (Material You) sur Android 12+.
 *
 * L'apport Expressive vient de [MotionScheme.expressive] (ressorts « vivants » automatiques sur
 * tous les composants Material) + [KairosShapes] (échelle de formes arrondie).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KairosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && dynamicColorSupported -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    // Les icônes des barres système suivent le thème **de l'app** (réglage), pas le mode du système :
    // en clair forcé sur un téléphone en sombre, on force des icônes sombres (et inversement).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = KairosShapes,
        typography = Typography,
        content = content,
    )
}
