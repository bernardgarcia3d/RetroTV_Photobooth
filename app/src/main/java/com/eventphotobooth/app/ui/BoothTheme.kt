package com.eventphotobooth.app.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object BoothPalette {
    // Reference-derived booth palette: cream plaster, painted green panels, and red curtain.
    val paper = Color(0xFFF4ECE2)
    val paperLight = Color(0xFFFFFBF4)
    val paperPanel = Color(0xFFDED4C6)
    val ink = Color(0xFF20231F)
    val inkMuted = Color(0xFF5E625B)
    val forest = Color(0xFF485A49)
    val forestLight = Color(0xFF6B7C6B)
    val oxblood = Color(0xFF913A32)
    val oxbloodDark = Color(0xFF61241F)
    val warmAccent = Color(0xFFD6A62F)
    val mint = Color(0xFFA8D8AE)
    val electricCyan = Color(0xFF31BFC2)
    val magenta = Color(0xFFC33E73)
    val purple = Color(0xFF71518F)
    val lime = Color(0xFFA7C94B)
    val error = Color(0xFFBA1A1A)

    // System surfaces intentionally remain close to the physical booth palette
    // so guest and operator views feel like parts of the same machine.
    val chrome = Color(0xFF172225)
    val chromeRaised = Color(0xFF263035)
    val chromeText = Color(0xFFD6E6DF)
    val chromeMuted = Color(0xFF9AA8A2)
    val border = Color(0xFFD5CBB8)
    val focus = Color(0xFF31BFC2)
}

internal val BoothTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.55.sp),
)

internal val BoothShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal val BoothLightColors = lightColorScheme(
    primary = BoothPalette.forest,
    onPrimary = BoothPalette.paperLight,
    secondary = BoothPalette.oxblood,
    onSecondary = BoothPalette.paperLight,
    background = BoothPalette.paper,
    onBackground = BoothPalette.ink,
    surface = BoothPalette.paperLight,
    onSurface = BoothPalette.ink,
    surfaceVariant = BoothPalette.paperPanel,
    onSurfaceVariant = BoothPalette.inkMuted,
    error = BoothPalette.error,
    onError = BoothPalette.paperLight,
)

internal val BoothDarkColors = darkColorScheme(
    primary = BoothPalette.mint,
    secondary = Color(0xFFF0A69D),
    background = Color(0xFF171A18),
    surface = Color(0xFF222722),
)
