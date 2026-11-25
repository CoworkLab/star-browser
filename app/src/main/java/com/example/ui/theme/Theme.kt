package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    DARK("Charcoal"),
    LIGHT("Soft Light"),
    SYSTEM("System")
}

enum class TextScalePreset(val label: String, val multiplier: Float) {
    COMPACT("Compact", 0.92f),
    STANDARD("Default", 1.0f),
    COMFORTABLE("Large", 1.1f)
}

val StarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun TextStyle.scaled(factor: Float): TextStyle =
    if (factor == 1f) this else copy(
        fontSize = fontSize * factor,
        lineHeight = lineHeight * factor
    )

private fun buildScaledTypography(scale: TextScalePreset): Typography {
    val f = scale.multiplier
    if (f == 1f) return StarTypography
    return Typography(
        displaySmall = StarTypography.displaySmall.scaled(f),
        headlineMedium = StarTypography.headlineMedium.scaled(f),
        headlineSmall = StarTypography.headlineSmall.scaled(f),
        titleLarge = StarTypography.titleLarge.scaled(f),
        titleMedium = StarTypography.titleMedium.scaled(f),
        titleSmall = StarTypography.titleSmall.scaled(f),
        bodyLarge = StarTypography.bodyLarge.scaled(f),
        bodyMedium = StarTypography.bodyMedium.scaled(f),
        bodySmall = StarTypography.bodySmall.scaled(f),
        labelLarge = StarTypography.labelLarge.scaled(f),
        labelMedium = StarTypography.labelMedium.scaled(f),
        labelSmall = StarTypography.labelSmall.scaled(f)
    )
}

@Composable
fun StarBrowserTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.LAVENDER,
    textScalePreset: TextScalePreset = TextScalePreset.STANDARD,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = remember(isDark, accentPreset) {
        if (isDark) starDarkColorScheme(accentPreset) else starLightColorScheme(accentPreset)
    }
    val semanticColors = remember(isDark, accentPreset) {
        if (isDark) darkStarSemanticColors(accentPreset) else lightStarSemanticColors(accentPreset)
    }
    val typography = remember(textScalePreset) { buildScaledTypography(textScalePreset) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalStarSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = StarShapes,
            content = content
        )
    }
}
