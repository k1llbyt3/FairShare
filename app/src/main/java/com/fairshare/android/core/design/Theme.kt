package com.fairshare.android.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorPalette = FSColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    surfaceStrong = DarkSurfaceStrong,
    border = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    disabled = DarkDisabled,
    accent = DarkAccent,
    accentPressed = DarkAccentPressed,
    accentSoft = DarkAccentSoft,
    positive = DarkPositive,
    positiveSoft = DarkPositiveSoft,
    negative = DarkNegative,
    negativeSoft = DarkNegativeSoft,
    info = DarkInfo,
    infoSoft = DarkInfoSoft,
    isDark = true
)

private val LightColorPalette = FSColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceElevated,
    surfaceStrong = LightSurfaceStrong,
    border = LightBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    disabled = LightDisabled,
    accent = LightAccent,
    accentPressed = LightAccentPressed,
    accentSoft = LightAccentSoft,
    positive = LightPositive,
    positiveSoft = LightPositiveSoft,
    negative = LightNegative,
    negativeSoft = LightNegativeSoft,
    info = LightInfo,
    infoSoft = LightInfoSoft,
    isDark = false
)

object FairShareTheme {
    val colors: FSColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFSColors.current

    val typography: FSTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalFSTypography.current

    val shapes: FSShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalFSShapes.current

    val spacing: FSSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalFSSpacing.current
}

@Composable
fun FairShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    val typography = FSTypography()
    val shapes = FSShapes()
    val spacing = FSSpacing()

    CompositionLocalProvider(
        LocalFSColors provides colors,
        LocalFSTypography provides typography,
        LocalFSShapes provides shapes,
        LocalFSSpacing provides spacing,
        content = content
    )
}
