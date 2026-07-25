package com.hal.ctrlfreak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// docs/ctrl-freak-android-ui-spec.md §1 — every value here is prescribed,
// not chosen. No raw hex/dp/sp anywhere outside this file.

object CfColor {
    val Background = Color(0xFF0E0F17)
    val Surface = Color(0xFF171925)
    val SurfaceRaised = Color(0xFF1E2130)
    val Inset = Color(0xFF0A0B12)
    val Ink = Color(0xFFECEAF3)
    val InkMuted = Color(0xFF9A9CB3)
    val InkFaint = Color(0xFF6B6D84)
    val Rule = Color(0xFF2A2C3D)
    val Accent = Color(0xFFE0A75E)
    val Cyan = Color(0xFF74C7D4)
    val Danger = Color(0xFFEA6F67)
}

object CfRadius {
    val Small = 6.dp
    val Card = 10.dp
    val Large = 14.dp
}

object CfSpace {
    val S4 = 4.dp
    val S6 = 6.dp
    val S7 = 7.dp
    val S8 = 8.dp
    val S9 = 9.dp
    val S10 = 10.dp
    val S11 = 11.dp
    val S12 = 12.dp
    val S14 = 14.dp
    val S16 = 16.dp
    val S24 = 24.dp
    val S30 = 30.dp
    val TapTarget = 44.dp
}

// Spec calls for bundled IBM Plex Mono/Sans (res/font/*.ttf) rather than
// system fallback. Those font files aren't bundled in this pass — using
// the closest honest system substitutes until they are, not silently
// pretending this is done. Swap these two lines once the .ttf assets are
// added under res/font/ and everything else (CfType below) is unaffected.
val CfMono: FontFamily = FontFamily.Monospace
val CfSans: FontFamily = FontFamily.SansSerif

object CfType {
    val Wordmark = TextStyle(fontFamily = CfMono, fontWeight = FontWeight.Medium, fontSize = 18.sp)
    val ScreenTitle = TextStyle(
        fontFamily = CfMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = CfColor.Ink,
    )
    val Label = TextStyle(
        fontFamily = CfMono, fontSize = 11.sp, letterSpacing = 0.6.sp, color = CfColor.InkFaint,
    )
    val Meta = TextStyle(fontFamily = CfMono, fontSize = 11.sp, color = CfColor.InkFaint)
    val NavItem = TextStyle(fontFamily = CfMono, fontSize = 10.sp, letterSpacing = 0.4.sp)
    val Chip = TextStyle(fontFamily = CfMono, fontSize = 12.sp)
    val Button = TextStyle(
        fontFamily = CfMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = CfColor.Background,
    )
    val Body = TextStyle(fontFamily = CfSans, fontSize = 14.sp, color = CfColor.Ink, lineHeight = 21.sp)
    val BodyMuted = TextStyle(
        fontFamily = CfSans, fontSize = 13.sp, color = CfColor.InkMuted, lineHeight = 19.5.sp,
    )
    val EditorBody = TextStyle(
        fontFamily = CfSans, fontSize = 15.sp, color = CfColor.Ink, lineHeight = 22.5.sp,
    )
    val ListTitle = TextStyle(fontFamily = CfSans, fontSize = 14.sp, color = CfColor.Ink)
}

private val CfDarkColorScheme = darkColorScheme(
    background = CfColor.Background,
    surface = CfColor.Surface,
    surfaceVariant = CfColor.SurfaceRaised,
    primary = CfColor.Accent,
    onPrimary = CfColor.Background,
    onSurface = CfColor.Ink,
    onBackground = CfColor.Ink,
    error = CfColor.Danger,
    outline = CfColor.Rule,
)

@Composable
fun CfTheme(content: @Composable () -> Unit) {
    // No dynamicColorScheme — the palette above is fixed per spec §6.
    MaterialTheme(colorScheme = CfDarkColorScheme, content = content)
}
