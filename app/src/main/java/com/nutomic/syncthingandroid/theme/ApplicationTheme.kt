package com.nutomic.syncthingandroid.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import me.zhanghai.compose.preference.preferenceTheme

/**
 * Smartisan design language palette:
 * light gray base (#F0F0F0) + white cards + dark ink (#3B3939, never pure black)
 * + single yellow-green accent (#A8C86A, Smartisan signature action color).
 * Dynamic (Material You) color extraction is intentionally NOT used.
 */
private val AccentGreen = Color(0xFFA8C86A)
private val InkDark = Color(0xFF3B3939)
private val InkMuted = Color(0xFFA8AAA9)
private val Hairline = Color(0xFFE3E4E3)

private val LightBackground = Color(0xFFF0F0F0)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEFEFED)

private val DarkBackground = Color(0xFF171C21)
private val DarkSurface = Color(0xFF1D2329)
private val DarkSurfaceVariant = Color(0xFF272F37)
private val DarkInk = Color(0xFFE1E4E8)
private val DarkInkMuted = Color(0xFFA8AAA9)

private val LightColors = lightColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF1F2A10),
    primaryContainer = Color(0xFFE4EFCB),
    onPrimaryContainer = Color(0xFF42501F),
    secondary = Color(0xFF6E8F3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EFCB),
    onSecondaryContainer = Color(0xFF42501F),
    tertiary = Color(0xFFC0392B),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = InkDark,
    surface = LightSurface,
    onSurface = InkDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF1F2A10),
    primaryContainer = Color(0xFF46552A),
    onPrimaryContainer = Color(0xFFD6E7B4),
    secondary = AccentGreen,
    onSecondary = Color(0xFF1F2A10),
    secondaryContainer = Color(0xFF46552A),
    onSecondaryContainer = Color(0xFFD6E7B4),
    tertiary = Color(0xFFE06A55),
    onTertiary = Color(0xFF2A0E08),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = Color(0xFF3A424A),
)

/** Smartisan: HarmonyOS Sans across all Compose text. */
private val HarmonySans = FontFamily(
    Font(R.font.harmonyos_sans_sc_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_sc_bold, FontWeight.Bold),
)

private fun smartisanTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = HarmonySans),
        displayMedium = base.displayMedium.copy(fontFamily = HarmonySans),
        displaySmall = base.displaySmall.copy(fontFamily = HarmonySans),
        headlineLarge = base.headlineLarge.copy(fontFamily = HarmonySans),
        headlineMedium = base.headlineMedium.copy(fontFamily = HarmonySans),
        headlineSmall = base.headlineSmall.copy(fontFamily = HarmonySans),
        titleLarge = base.titleLarge.copy(fontFamily = HarmonySans),
        titleMedium = base.titleMedium.copy(fontFamily = HarmonySans),
        titleSmall = base.titleSmall.copy(fontFamily = HarmonySans),
        bodyLarge = base.bodyLarge.copy(fontFamily = HarmonySans),
        bodyMedium = base.bodyMedium.copy(fontFamily = HarmonySans),
        bodySmall = base.bodySmall.copy(fontFamily = HarmonySans),
        labelLarge = base.labelLarge.copy(fontFamily = HarmonySans),
        labelMedium = base.labelMedium.copy(fontFamily = HarmonySans),
        labelSmall = base.labelSmall.copy(fontFamily = HarmonySans),
    )
}

/** Smartisan shapes: restrained radii, never the 28dp M3 default. */
private val SmartisanShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val colorScheme = if (isDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = smartisanTypography(),
        shapes = SmartisanShapes,
    ) {
        // Smartisan: no press ripple / grey shadow animation anywhere.
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            // Smartisan preference typography: the library defaults to M3 titleLarge (22sp),
            // which wrapped long Chinese titles. All preference rows render at the same
            // 15.5sp ink / 12.5sp weak-gray scale used by the XML settings rows.
            // Based on theme typography styles so HarmonyOS Sans is kept.
            val typo = MaterialTheme.typography
            ProvidePreferenceTheme(
                preferenceTheme(
                    titleColor = colorScheme.onSurface,
                    titleTextStyle = typo.titleLarge.copy(fontSize = 15.5.sp, lineHeight = 20.sp),
                    summaryColor = colorScheme.onSurfaceVariant,
                    summaryTextStyle = typo.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                )
            ) {
                content()
            }
        }
    }
}
