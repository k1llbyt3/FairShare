package com.fairshare.android.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Strict Flat Colors for FairShare.
 * No gradients, no purple gradients, no glassmorphism.
 * As defined in specs/Design.md Section 7.
 */

// Dark Theme (Default)
val DarkBackground = Color(0xFF0D0F11)
val DarkSurface = Color(0xFF14171A)
val DarkSurfaceElevated = Color(0xFF191D21)
val DarkSurfaceStrong = Color(0xFF20252A)
val DarkBorder = Color(0xFF2A3036)

val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFFCAD1D8)
val DarkTextTertiary = Color(0xFFA5ADB6)
val DarkDisabled = Color(0xFF6B7280)

val DarkAccent = Color(0xFFF2B84B)
val DarkAccentPressed = Color(0xFFDDA33B)
val DarkAccentSoft = Color(0xFF2A2418)

val DarkPositive = Color(0xFF55C58A)
val DarkPositiveSoft = Color(0xFF14241C)
val DarkNegative = Color(0xFFE47777)
val DarkNegativeSoft = Color(0xFF28191A)
val DarkInfo = Color(0xFF79A9E8)
val DarkInfoSoft = Color(0xFF17202B)

// Light Theme
val LightBackground = Color(0xFFF7F6F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFBFAF7)
val LightSurfaceStrong = Color(0xFFF0EEE8)
val LightBorder = Color(0xFFD9D6CF)

val LightTextPrimary = Color(0xFF111827)
val LightTextSecondary = Color(0xFF374151)
val LightTextTertiary = Color(0xFF4B5563)
val LightDisabled = Color(0xFFB0B5BA)

val LightAccent = Color(0xFFB77813)
val LightAccentPressed = Color(0xFF955E0C)
val LightAccentSoft = Color(0xFFFFF0D2)

val LightPositive = Color(0xFF227A50)
val LightPositiveSoft = Color(0xFFEAF5EE)
val LightNegative = Color(0xFFB84848)
val LightNegativeSoft = Color(0xFFFCEAEA)
val LightInfo = Color(0xFF376AA3)
val LightInfoSoft = Color(0xFFE8F1FC)

@Immutable
data class FSColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceStrong: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val disabled: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val positive: Color,
    val positiveSoft: Color,
    val negative: Color,
    val negativeSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val isDark: Boolean
)

val LocalFSColors = staticCompositionLocalOf {
    FSColors(
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
}
