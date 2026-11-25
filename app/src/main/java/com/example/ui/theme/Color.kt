package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Soft Charcoal Grey Dark Tokens
val CharcoalCanvas = Color(0xFF1C1D20)
val CharcoalSurfaceLow = Color(0xFF222428)
val CharcoalSurface = Color(0xFF27292E)
val CharcoalSurfaceHigh = Color(0xFF303339)
val CharcoalSurfaceHighest = Color(0xFF3A3E46)
val CharcoalHairline = Color(0xFF353840)
val CharcoalOnSurface = Color(0xFFF3F4F6)
val CharcoalOnSurfaceVariant = Color(0xFFA8AFB9)

// Soft Light Grey White-Mode Tokens
val MistCanvas = Color(0xFFF3F4F6)
val MistSurfaceLowest = Color(0xFFFFFFFF)
val MistSurfaceLow = Color(0xFFEAECEF)
val MistSurface = Color(0xFFE5E7EB)
val MistSurfaceHigh = Color(0xFFDCDFE5)
val MistSurfaceHighest = Color(0xFFD1D5DC)
val MistHairline = Color(0xFFD5D9E2)
val MistOnSurface = Color(0xFF181A1F)
val MistOnSurfaceVariant = Color(0xFF575D68)

enum class AccentPreset(
    val label: String,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color
) {
    LAVENDER(
        label = "Lavender",
        darkPrimary = Color(0xFFC4A7FF),
        darkContainer = Color(0xFF352D52),
        darkOnContainer = Color(0xFFEDE8FF),
        lightPrimary = Color(0xFF5E47CC),
        lightContainer = Color(0xFFEAE3FF),
        lightOnContainer = Color(0xFF1E134D)
    ),
    STARLIGHT(
        label = "Starlight",
        darkPrimary = Color(0xFF88D1FF),
        darkContainer = Color(0xFF1E364D),
        darkOnContainer = Color(0xFFDDF2FF),
        lightPrimary = Color(0xFF1B6EC2),
        lightContainer = Color(0xFFDCEEFF),
        lightOnContainer = Color(0xFF0A2C52)
    ),
    SAGE(
        label = "Mint Sage",
        darkPrimary = Color(0xFF82E2B8),
        darkContainer = Color(0xFF1B3A2D),
        darkOnContainer = Color(0xFFD9FBE9),
        lightPrimary = Color(0xFF127A54),
        lightContainer = Color(0xFFD6F6E8),
        lightOnContainer = Color(0xFF093825)
    ),
    ROSE(
        label = "Warm Rose",
        darkPrimary = Color(0xFFF4B1CE),
        darkContainer = Color(0xFF472638),
        darkOnContainer = Color(0xFFFFE6F1),
        lightPrimary = Color(0xFFB43C72),
        lightContainer = Color(0xFFFFE2EE),
        lightOnContainer = Color(0xFF470D29)
    )
}

val StarlightBlueDark = Color(0xFF8FD3FF)
val StarlightContainerDark = Color(0xFF1E3246)
val StarlightOnContainerDark = Color(0xFFD9F0FF)
val StarlightBlueLight = Color(0xFF1B6EC2)
val StarlightContainerLight = Color(0xFFDDEEFF)
val StarlightOnContainerLight = Color(0xFF0A2C52)

// Semantic Status
val EmeraldSuccess = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonErrorDark = Color(0xFFF87171)
val CrimsonErrorLight = Color(0xFFDC2626)

fun starDarkColorScheme(accent: AccentPreset = AccentPreset.LAVENDER): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF17181B),
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = accent.darkPrimary.copy(alpha = 0.85f),
    onSecondary = Color(0xFF17181B),
    secondaryContainer = CharcoalSurfaceHigh,
    onSecondaryContainer = CharcoalOnSurface,
    tertiary = StarlightBlueDark,
    onTertiary = Color(0xFF17181B),
    tertiaryContainer = StarlightContainerDark,
    onTertiaryContainer = StarlightOnContainerDark,
    background = CharcoalCanvas,
    onBackground = CharcoalOnSurface,
    surface = CharcoalCanvas,
    onSurface = CharcoalOnSurface,
    surfaceVariant = CharcoalSurface,
    onSurfaceVariant = CharcoalOnSurfaceVariant,
    surfaceContainerLowest = CharcoalCanvas,
    surfaceContainerLow = CharcoalSurfaceLow,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalSurfaceHigh,
    surfaceContainerHighest = CharcoalSurfaceHighest,
    outline = Color(0xFF4B505B),
    outlineVariant = CharcoalHairline,
    error = CrimsonErrorDark,
    onError = Color(0xFF2B0B0E)
)

fun starLightColorScheme(accent: AccentPreset = AccentPreset.LAVENDER): ColorScheme = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    secondary = accent.lightPrimary.copy(alpha = 0.85f),
    onSecondary = Color.White,
    secondaryContainer = MistSurfaceHigh,
    onSecondaryContainer = MistOnSurface,
    tertiary = StarlightBlueLight,
    onTertiary = Color.White,
    tertiaryContainer = StarlightContainerLight,
    onTertiaryContainer = StarlightOnContainerLight,
    background = MistCanvas,
    onBackground = MistOnSurface,
    surface = MistCanvas,
    onSurface = MistOnSurface,
    surfaceVariant = MistSurface,
    onSurfaceVariant = MistOnSurfaceVariant,
    surfaceContainerLowest = MistSurfaceLowest,
    surfaceContainerLow = MistSurfaceLow,
    surfaceContainer = MistSurface,
    surfaceContainerHigh = MistSurfaceHigh,
    surfaceContainerHighest = MistSurfaceHighest,
    outline = Color(0xFF8B919E),
    outlineVariant = MistHairline,
    error = CrimsonErrorLight,
    onError = Color.White
)

@Immutable
data class StarSemanticColors(
    val success: Color,
    val warning: Color,
    val codeSurface: Color,
    val codeHeaderSurface: Color,
    val subtleGlow: Color,
    val studioAccentCardStart: Color,
    val studioAccentCardEnd: Color,
    val studioNestedCard: Color,
    val studioNestedCardText: Color
)

fun darkStarSemanticColors(accent: AccentPreset = AccentPreset.LAVENDER): StarSemanticColors = StarSemanticColors(
    success = EmeraldSuccess,
    warning = AmberWarning,
    codeSurface = Color(0xFF151619),
    codeHeaderSurface = Color(0xFF22242A),
    subtleGlow = accent.darkPrimary.copy(alpha = 0.14f),
    studioAccentCardStart = Color(0xFF87CEEB),
    studioAccentCardEnd = Color(0xFFA7E3C6),
    studioNestedCard = Color(0xF2FFFFFF),
    studioNestedCardText = Color(0xFF18191C)
)

fun lightStarSemanticColors(accent: AccentPreset = AccentPreset.LAVENDER): StarSemanticColors = StarSemanticColors(
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    codeSurface = Color(0xFF1E2026),
    codeHeaderSurface = Color(0xFF292C34),
    subtleGlow = accent.lightPrimary.copy(alpha = 0.08f),
    studioAccentCardStart = Color(0xFFB5E3F8),
    studioAccentCardEnd = Color(0xFFC4F0DA),
    studioNestedCard = Color(0xFFFFFFFF),
    studioNestedCardText = Color(0xFF18191C)
)

val LocalStarSemanticColors = staticCompositionLocalOf { darkStarSemanticColors() }
