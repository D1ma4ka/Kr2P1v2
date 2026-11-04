package ru.krapiva.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object KraPivaColors {
    val Ink = Color(0xFF171512)
    val Surface = Color(0xFF24201B)
    val Amber = Color(0xFFE9AF61)
    val Cream = Color(0xFFF6EFE3)
    val Muted = Color(0xFFC3B6A4)
    val Error = Color(0xFFFFB4AB)
}
object KraPivaSpacing {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val Section = 40.dp
}
private val Colors = darkColorScheme(
    primary = KraPivaColors.Amber, onPrimary = KraPivaColors.Ink,
    secondary = KraPivaColors.Cream, onSecondary = KraPivaColors.Ink,
    background = KraPivaColors.Ink, onBackground = KraPivaColors.Cream,
    surface = KraPivaColors.Surface, onSurface = KraPivaColors.Cream,
    onSurfaceVariant = KraPivaColors.Muted,
    error = KraPivaColors.Error, onError = KraPivaColors.Ink,
)
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp,
        lineHeight = 16.sp, letterSpacing = 1.5.sp),
)
@Composable
fun KraPivaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp)),
        content = content)
}
