package com.nexplay.dronepreflight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Paleta command-center v2 (zielona) ──
object OpsColors {
    val BgBase = Color(0xFF0A1220)         // dark navy tło
    val BgPanel = Color(0xFF141E30)        // panele
    val BgPanelRaised = Color(0xFF1B2A3F)  // uniesione elementy
    val Grid = Color(0xFF2A3D55)           // border-y / dividery
    val Accent = Color(0xFF34D399)         // primary — mint/lime
    val AccentDim = Color(0xFF16A34A)      // ciemniejszy akcent
    val Amber = Color(0xFFF59E0B)          // ostrzeżenia
    val TextPrimary = Color(0xFFE5EEF7)    // jasny
    val TextSecondary = Color(0xFF94A3B8)  // szary
    val Danger = Color(0xFFEF4444)
}

private val OpsScheme = darkColorScheme(
    primary = OpsColors.Accent,
    onPrimary = Color(0xFF002F1B),
    primaryContainer = OpsColors.BgPanelRaised,
    onPrimaryContainer = OpsColors.Accent,
    secondary = OpsColors.Amber,
    onSecondary = Color(0xFF3B2600),
    background = OpsColors.BgBase,
    onBackground = OpsColors.TextPrimary,
    surface = OpsColors.BgPanel,
    onSurface = OpsColors.TextPrimary,
    surfaceVariant = OpsColors.BgPanelRaised,
    onSurfaceVariant = OpsColors.TextSecondary,
    outline = OpsColors.Grid,
    outlineVariant = OpsColors.Grid,
    error = OpsColors.Danger,
    onError = Color.White,
)

private val mono = FontFamily.Monospace
private val sans = FontFamily.Default

private val OpsTypography = Typography(
    displayLarge = TextStyle(fontFamily = sans, fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = sans, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = sans, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),

    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = mono, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),

    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),

    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),

    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
)

private val OpsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DronePreflightTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpsScheme,
        typography = OpsTypography,
        shapes = OpsShapes,
        content = content,
    )
}

object VerdictColors {
    val Go = Color(0xFF22C55E)      // green-500
    val Caution = Color(0xFFF59E0B) // amber-500
    val NoGo = Color(0xFFEF4444)    // red-500
}
