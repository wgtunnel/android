package com.zaneschepke.wireguardautotunnel.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zaneschepke.wireguardautotunnel.ui.LocalIsAndroidTV

private val DarkColorScheme =
    darkColorScheme(
        primary = ThemeColors.Dark.primary,
        surface = ThemeColors.Dark.surface,
        background = ThemeColors.Dark.background,
        secondary = ThemeColors.Dark.secondary,
        onSurface = ThemeColors.Dark.onSurface,
        onSecondaryContainer = ThemeColors.Dark.primary,
        outline = ThemeColors.Dark.outline,
        onBackground = ThemeColors.Dark.onBackground,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ThemeColors.Light.primary,
        surface = ThemeColors.Light.surface,
        background = ThemeColors.Light.background,
        secondary = ThemeColors.Light.secondary,
        onSurface = ThemeColors.Light.onSurface,
        onSecondaryContainer = ThemeColors.Light.primary,
        outline = ThemeColors.Light.outline,
        onBackground = ThemeColors.Light.onBackground,
    )

enum class Theme {
    AUTOMATIC,
    LIGHT,
    DARK,
    DARKER,
    AMOLED,
    DYNAMIC,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WireguardAutoTunnelTheme(theme: Theme = Theme.AUTOMATIC, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTv = LocalIsAndroidTV.current
    var isDark = isSystemInDarkTheme()
    val autoTheme = if (isDark) DarkColorScheme else LightColorScheme
    val colorScheme =
        when (theme) {
            Theme.AUTOMATIC -> autoTheme
            Theme.DARK -> {
                isDark = true
                DarkColorScheme
            }
            Theme.DARKER -> {
                isDark = true
                DarkColorScheme.copy(surface = BalticSea, background = BalticSea)
            }
            Theme.AMOLED -> {
                isDark = true
                DarkColorScheme.copy(
                    surface = Color.Black,
                    background = Color.Black,
                    primary = ElectricTeal,
                )
            }
            Theme.LIGHT -> {
                isDark = false
                LightColorScheme
            }
            Theme.DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDark) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(context)
                    }
                } else {
                    autoTheme
                }
            }
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // For API 33+, use WindowInsetsControllerCompat for appearance control
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val rippleConfig =
        RippleConfiguration(
            color = colorScheme.primary,
            rippleAlpha =
                if (isTv)
                    RippleAlpha(
                        pressedAlpha = 0.5f,
                        focusedAlpha = 0.4f,
                        draggedAlpha = 0.2f,
                        hoveredAlpha = 0.4f,
                    )
                else
                    RippleAlpha(
                        pressedAlpha = 0.2f,
                        focusedAlpha = 0.1f,
                        draggedAlpha = 0.2f,
                        hoveredAlpha = 0.1f,
                    ),
        )

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfig) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
