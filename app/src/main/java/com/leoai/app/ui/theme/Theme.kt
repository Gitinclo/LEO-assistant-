package com.leoai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LeoColors {
    val Bg = Color(0xFFFDFBF7)
    val Surface = Color(0xFFF4F1EA)
    val SurfaceElevated = Color(0xFFEBE7DD)
    val Primary = Color(0xFFC86A51)
    val PrimaryFg = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF7E947A)
    val SecondaryFg = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF2D332C)
    val TextSecondary = Color(0xFF5C665A)
    val TextMuted = Color(0xFF8A9488)
    val Border = Color(0xFFE2DDD0)
    val BorderFocus = Color(0xFFC86A51)
    val Danger = Color(0xFFB0463A)
}

object LeoRadius {
    val Sm = 8.dp
    val Md = 14.dp
    val Lg = 20.dp
    val Pill = 999.dp
}

private val LeoColorScheme = lightColorScheme(
    primary = LeoColors.Primary,
    onPrimary = LeoColors.PrimaryFg,
    secondary = LeoColors.Secondary,
    onSecondary = LeoColors.SecondaryFg,
    background = LeoColors.Bg,
    onBackground = LeoColors.TextPrimary,
    surface = LeoColors.Surface,
    onSurface = LeoColors.TextPrimary,
    surfaceVariant = LeoColors.SurfaceElevated,
    onSurfaceVariant = LeoColors.TextSecondary,
    outline = LeoColors.Border,
    error = LeoColors.Danger,
    onError = Color.White
)

val LeoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        color = LeoColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        color = LeoColors.TextSecondary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = LeoColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = LeoColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        color = LeoColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = LeoColors.TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LeoColors.TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = LeoColors.TextMuted
    )
)

val LeoShapes = Shapes(
    small = RoundedCornerShape(LeoRadius.Sm),
    medium = RoundedCornerShape(LeoRadius.Md),
    large = RoundedCornerShape(LeoRadius.Lg),
    extraLarge = RoundedCornerShape(LeoRadius.Pill)
)

@Composable
fun LeoAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LeoColorScheme,
        typography = LeoTypography,
        shapes = LeoShapes,
        content = content
    )
}
