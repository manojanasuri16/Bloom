package com.bloom.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bloom's semantic palette beyond Material's roles. */
@Immutable
data class BloomColors(
    val cream: Color, val surface: Color, val surfaceHigh: Color,
    val blush: Color, val rose: Color, val roseSoft: Color,
    val lavender: Color, val lavenderDeep: Color, val lavenderSoft: Color,
    val ink: Color, val inkMuted: Color, val hairline: Color, val onRose: Color,
    val dark: Boolean,
)

private val LightBloom = BloomColors(
    cream = Color(0xFFFBF6F0), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFF5ECE6),
    blush = Color(0xFFF4C6D0), rose = Color(0xFFC45A78), roseSoft = Color(0xFFFBE3E8),
    lavender = Color(0xFFC9B8EA), lavenderDeep = Color(0xFF6F58AE), lavenderSoft = Color(0xFFECE6F7),
    ink = Color(0xFF2B2230), inkMuted = Color(0xFF7D7283), hairline = Color(0x142B2230), onRose = Color.White,
    dark = false,
)
private val DarkBloom = BloomColors(
    cream = Color(0xFF17121C), surface = Color(0xFF221B29), surfaceHigh = Color(0xFF2D2435),
    blush = Color(0xFF8A4A5E), rose = Color(0xFFF0A0B6), roseSoft = Color(0xFF45283A),
    lavender = Color(0xFF8E7BC4), lavenderDeep = Color(0xFFC9B8EA), lavenderSoft = Color(0xFF30284A),
    ink = Color(0xFFF5EEF3), inkMuted = Color(0xFFA89DAE), hairline = Color(0x1FF5EEF3), onRose = Color(0xFF3A1424),
    dark = true,
)

val LocalBloom = staticCompositionLocalOf { LightBloom }

object Bloom {
    val colors: BloomColors @Composable get() = LocalBloom.current
}

private fun scheme(c: BloomColors): ColorScheme =
    run {
        if (c.dark) darkColorScheme(
            primary = c.rose, onPrimary = c.onRose, primaryContainer = c.roseSoft, onPrimaryContainer = c.ink,
            secondary = c.lavenderDeep, onSecondary = Color(0xFF241A3D), secondaryContainer = c.lavenderSoft,
            onSecondaryContainer = c.ink, background = c.cream, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.inkMuted,
            outline = c.hairline, outlineVariant = c.hairline, error = Color(0xFFFFB4AB),
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerHighest = c.surfaceHigh,
        ) else lightColorScheme(
            primary = c.rose, onPrimary = c.onRose, primaryContainer = c.roseSoft, onPrimaryContainer = c.ink,
            secondary = c.lavenderDeep, onSecondary = Color.White, secondaryContainer = c.lavenderSoft,
            onSecondaryContainer = c.ink, background = c.cream, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.inkMuted,
            outline = c.hairline, outlineVariant = c.hairline, error = Color(0xFFB3261E),
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerHighest = c.surfaceHigh,
        )
    }

/**
 * Optical-size-aware type scale: tracking tightens as size grows, leading loosens for body text
 * (Apple's "Details of UI Typography" rules). Rounded geometry comes from the system sans.
 */
private val Sans = FontFamily.SansSerif
private fun ts(w: FontWeight, size: Float, line: Float, track: Float) =
    TextStyle(fontFamily = Sans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp)

private val BloomType = Typography(
    displayLarge = ts(FontWeight.Light, 88f, 88f, -3f),
    displayMedium = ts(FontWeight.Light, 56f, 58f, -1.8f),
    headlineLarge = ts(FontWeight.SemiBold, 32f, 36f, -0.9f),
    headlineMedium = ts(FontWeight.SemiBold, 26f, 30f, -0.6f),
    headlineSmall = ts(FontWeight.SemiBold, 21f, 26f, -0.4f),
    titleLarge = ts(FontWeight.SemiBold, 19f, 24f, -0.3f),
    titleMedium = ts(FontWeight.SemiBold, 16f, 22f, -0.15f),
    titleSmall = ts(FontWeight.Medium, 14f, 20f, 0f),
    bodyLarge = ts(FontWeight.Normal, 16f, 24f, 0f),
    bodyMedium = ts(FontWeight.Normal, 14f, 21f, 0.05f),
    bodySmall = ts(FontWeight.Normal, 12f, 17f, 0.15f),
    labelLarge = ts(FontWeight.SemiBold, 15f, 20f, -0.1f),
    labelMedium = ts(FontWeight.Medium, 12f, 16f, 0.3f),
    labelSmall = ts(FontWeight.SemiBold, 11f, 14f, 0.8f),
)

private val BloomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BloomTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val c = if (dark) DarkBloom else LightBloom
    CompositionLocalProvider(LocalBloom provides c, androidx.compose.material3.LocalContentColor provides c.ink) {
        MaterialTheme(colorScheme = scheme(c), typography = BloomType, shapes = BloomShapes, content = content)
    }
}
