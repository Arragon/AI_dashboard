package com.subscriptiontracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object AppPalette {
    val Juniper = Color(0xFF356B59)
    val JuniperSoft = Color(0xFFD8E9E0)
    val JuniperDark = Color(0xFF173C30)
    val JuniperNight = Color(0xFF8AC4AA)
    val JuniperNightContainer = Color(0xFF244A3C)
    val Paper = Color(0xFFF4F2EC)
    val PaperRaised = Color(0xFFFCFAF6)
    val PaperMuted = Color(0xFFECEAE3)
    val Ink = Color(0xFF1B201D)
    val InkMuted = Color(0xFF626A65)
    val Line = Color(0xFFD9DDD8)
    val Charcoal = Color(0xFF101512)
    val CharcoalRaised = Color(0xFF171D19)
    val CharcoalMuted = Color(0xFF202822)
    val Mist = Color(0xFFE7ECE8)
    val MistMuted = Color(0xFFAEB8B1)
    val NightLine = Color(0xFF354039)
    val Terracotta = Color(0xFFA44F43)
    val TerracottaNight = Color(0xFFFFB4A8)
}

internal val LightAppColors = lightColorScheme(
    primary = AppPalette.Juniper,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AppPalette.JuniperSoft,
    onPrimaryContainer = AppPalette.JuniperDark,
    background = AppPalette.Paper,
    onBackground = AppPalette.Ink,
    surface = AppPalette.PaperRaised,
    onSurface = AppPalette.Ink,
    surfaceVariant = AppPalette.PaperMuted,
    onSurfaceVariant = AppPalette.InkMuted,
    outline = AppPalette.Line,
    outlineVariant = Color(0xFFE4E7E2),
    error = AppPalette.Terracotta,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5DDD8),
    onErrorContainer = Color(0xFF61251D),
    scrim = Color(0xFF111612),
)

internal val DarkAppColors = darkColorScheme(
    primary = AppPalette.JuniperNight,
    onPrimary = Color(0xFF0D2A21),
    primaryContainer = AppPalette.JuniperNightContainer,
    onPrimaryContainer = Color(0xFFC5E8D5),
    background = AppPalette.Charcoal,
    onBackground = AppPalette.Mist,
    surface = AppPalette.CharcoalRaised,
    onSurface = AppPalette.Mist,
    surfaceVariant = AppPalette.CharcoalMuted,
    onSurfaceVariant = AppPalette.MistMuted,
    outline = AppPalette.NightLine,
    outlineVariant = Color(0xFF29322D),
    error = AppPalette.TerracottaNight,
    onError = Color(0xFF5F160E),
    errorContainer = Color(0xFF7F2D24),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0xFF070A08),
)

internal fun appColorScheme(darkTheme: Boolean) = if (darkTheme) DarkAppColors else LightAppColors

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun SubscriptionTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(darkTheme),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
