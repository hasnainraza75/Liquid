package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.LuxThemeMode

@Immutable
data class LiquidGlassTokens(
    val isDark: Boolean,
    val blurRadiusDp: Float,
    val glassOpacity: Float,
    val panelTopFill: Color,
    val panelBottomFill: Color,
    val tileInactiveFill: Color,
    val specularBorderTop: Color,
    val specularBorderMid: Color,
    val specularBorderBottom: Color,
    val sliderTrackFill: Color,
    val sliderLiquidTop: Color,
    val sliderLiquidBottom: Color,
    val primaryInk: Color,
    val secondaryInk: Color,
    val mutedInk: Color,
    val scrimTint: Color
)

val LocalLiquidGlassTokens = staticCompositionLocalOf {
    buildLiquidGlassTokens(isDark = true, blurRadiusDp = 28f, glassOpacity = 0.24f)
}

fun buildLiquidGlassTokens(
    isDark: Boolean,
    blurRadiusDp: Float,
    glassOpacity: Float
): LiquidGlassTokens {
    val alpha = glassOpacity.coerceIn(0.14f, 0.42f)
    return if (isDark) {
        LiquidGlassTokens(
            isDark = true,
            blurRadiusDp = blurRadiusDp,
            glassOpacity = alpha,
            panelTopFill = Color(0xFFFFFFFF).copy(alpha = (alpha * 0.62f).coerceIn(0.08f, 0.28f)),
            panelBottomFill = Color(0xFF091326).copy(alpha = (alpha * 1.95f).coerceIn(0.32f, 0.72f)),
            tileInactiveFill = Color(0xFFFFFFFF).copy(alpha = (alpha * 0.56f).coerceIn(0.10f, 0.24f)),
            specularBorderTop = Color(0xFFFFFFFF).copy(alpha = 0.52f),
            specularBorderMid = Color(0xFF9CD8FF).copy(alpha = 0.18f),
            specularBorderBottom = Color(0xFFFFFFFF).copy(alpha = 0.10f),
            sliderTrackFill = Color(0xFF0A1222).copy(alpha = 0.54f),
            sliderLiquidTop = Color(0xFFFFFFFF).copy(alpha = 0.94f),
            sliderLiquidBottom = Color(0xFFD8F3FF).copy(alpha = 0.86f),
            primaryInk = OpalPearl,
            secondaryInk = MistSlate,
            mutedInk = MistSlate.copy(alpha = 0.72f),
            scrimTint = ObsidianVoid.copy(alpha = 0.42f)
        )
    } else {
        LiquidGlassTokens(
            isDark = false,
            blurRadiusDp = blurRadiusDp,
            glassOpacity = alpha,
            panelTopFill = Color(0xFFFFFFFF).copy(alpha = (alpha + 0.44f).coerceIn(0.56f, 0.84f)),
            panelBottomFill = Color(0xFFE2EEFA).copy(alpha = (alpha + 0.34f).coerceIn(0.46f, 0.74f)),
            tileInactiveFill = Color(0xFFFFFFFF).copy(alpha = (alpha + 0.36f).coerceIn(0.48f, 0.76f)),
            specularBorderTop = Color(0xFFFFFFFF).copy(alpha = 0.92f),
            specularBorderMid = Color(0xFF7CB8E8).copy(alpha = 0.30f),
            specularBorderBottom = Color(0xFF0B162C).copy(alpha = 0.10f),
            sliderTrackFill = Color(0xFF0B162C).copy(alpha = 0.14f),
            sliderLiquidTop = Color(0xFFFFFFFF).copy(alpha = 0.98f),
            sliderLiquidBottom = Color(0xFFEAF6FF).copy(alpha = 0.92f),
            primaryInk = DeepCobaltInk,
            secondaryInk = SlateInk,
            mutedInk = SlateInk.copy(alpha = 0.76f),
            scrimTint = PearlAtmosphere.copy(alpha = 0.24f)
        )
    }
}

private val LuxDarkColorScheme = darkColorScheme(
    primary = GlacialCyan,
    onPrimary = ObsidianVoid,
    secondary = LiquidAzure,
    onSecondary = OpalPearl,
    tertiary = GlowDnd,
    background = ObsidianVoid,
    onBackground = OpalPearl,
    surface = DeepSapphireSurface,
    onSurface = OpalPearl,
    surfaceVariant = Color(0xFF17253D),
    onSurfaceVariant = MistSlate,
    outline = Color(0xFF3E5A80)
)

private val LuxLightColorScheme = lightColorScheme(
    primary = CeruleanAccent,
    onPrimary = Color.White,
    secondary = SkyRefraction,
    onSecondary = Color.White,
    tertiary = GlowDnd,
    background = PearlAtmosphere,
    onBackground = DeepCobaltInk,
    surface = FrostAlabaster,
    onSurface = DeepCobaltInk,
    surfaceVariant = Color(0xFFDCE9F7),
    onSurfaceVariant = SlateInk,
    outline = Color(0xFF8AA5C4)
)

@Composable
fun LuxControlTheme(
    themeMode: LuxThemeMode = LuxThemeMode.DARK,
    blurRadiusDp: Float = 28f,
    glassOpacity: Float = 0.24f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        LuxThemeMode.DARK -> true
        LuxThemeMode.LIGHT -> false
        LuxThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = if (useDark) LuxDarkColorScheme else LuxLightColorScheme
    val glassTokens = buildLiquidGlassTokens(
        isDark = useDark,
        blurRadiusDp = blurRadiusDp,
        glassOpacity = glassOpacity
    )

    CompositionLocalProvider(LocalLiquidGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LuxTypography,
            content = content
        )
    }
}

// Keep template compatibility symbol for any external previews
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    LuxControlTheme(
        themeMode = if (darkTheme) LuxThemeMode.DARK else LuxThemeMode.LIGHT,
        content = content
    )
}
