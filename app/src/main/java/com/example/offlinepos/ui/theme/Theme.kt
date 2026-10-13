package com.example.offlinepos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PosPrimaryDark,
    onPrimary = PosOnPrimaryDark,
    primaryContainer = PosPrimaryContainerDark,
    onPrimaryContainer = PosOnPrimaryContainerDark,
    secondary = PosSecondary,
    onSecondary = PosOnSecondary,
    secondaryContainer = PosSecondaryContainer,
    onSecondaryContainer = PosOnSecondaryContainer,
    tertiary = PosTertiary,
    background = PosBackgroundDark,
    surface = PosSurfaceDark,
    surfaceVariant = PosSurfaceVariantDark,
    onBackground = PosOnSurfaceDark,
    onSurface = PosOnSurfaceDark,
    onSurfaceVariant = PosOnSurfaceVariantDark,
    outline = PosOutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PosPrimary,
    onPrimary = PosOnPrimary,
    primaryContainer = PosPrimaryContainer,
    onPrimaryContainer = PosOnPrimaryContainer,
    secondary = PosSecondary,
    onSecondary = PosOnSecondary,
    secondaryContainer = PosSecondaryContainer,
    onSecondaryContainer = PosOnSecondaryContainer,
    tertiary = PosTertiary,
    background = PosBackground,
    surface = PosSurface,
    surfaceVariant = PosSurfaceVariant,
    onBackground = PosOnSurface,
    onSurface = PosOnSurface,
    onSurfaceVariant = PosOnSurfaceVariant,
    outline = PosOutline,
)

@Composable
fun OfflinePOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded consistent colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}