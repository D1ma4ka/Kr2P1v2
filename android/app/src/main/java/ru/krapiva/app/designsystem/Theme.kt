package ru.krapiva.app.designsystem

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { DARK, LIGHT }

/** Device preference only; never contains account data or credentials. */
class ThemePreference(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var mode by mutableStateOf(runCatching {
        ThemeMode.valueOf(preferences.getString("theme", "DARK")!!)
    }.getOrDefault(ThemeMode.DARK))
        private set
    fun select(value: ThemeMode) { mode = value; preferences.edit().putString("theme", value.name).apply() }
}
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }
val LocalThemeSelection = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

/** Legacy names retained so all P0–P6 screens follow the selected palette. */
object KraPivaColors {
    val Ink: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Amber: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Cream: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val Error: Color @Composable get() = MaterialTheme.colorScheme.error
    val Hop = Color(0xFF78B936)
    val HopLight: Color @Composable get() = if (LocalThemeMode.current == ThemeMode.DARK) Color(0xFF9ACD45) else Color(0xFF456A16)
}
object ClubTokens {
    val Amber = Color(0xFFF3AF45)
    val OnAmber = Color(0xFF171B1C)
    val CardShape = RoundedCornerShape(16.dp)
    val ControlShape = RoundedCornerShape(12.dp)
}
object KraPivaSpacing {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val Section = 32.dp
}
private val Dark = darkColorScheme(
    primary = ClubTokens.Amber, onPrimary = ClubTokens.OnAmber,
    primaryContainer = Color(0xFF3A2D1B), onPrimaryContainer = Color(0xFFFFD89A),
    secondary = Color(0xFFF4EEE4), onSecondary = Color(0xFF101315),
    background = Color(0xFF101315), onBackground = Color(0xFFF4EEE4),
    surface = Color(0xFF1D2226), onSurface = Color(0xFFF4EEE4),
    surfaceVariant = Color(0xFF252B2E), onSurfaceVariant = Color(0xFFB7B9B7),
    outline = Color(0xFF767D80), outlineVariant = Color(0xFF343B3E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601410),
)
private val Light = lightColorScheme(
    primary = Color(0xFF935500), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1AF), onPrimaryContainer = Color(0xFF392300),
    secondary = Color(0xFF202322), onSecondary = Color.White,
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF202322),
    surface = Color.White, onSurface = Color(0xFF202322),
    surfaceVariant = Color(0xFFF0EDE6), onSurfaceVariant = Color(0xFF62665F),
    outline = Color(0xFF797D76), outlineVariant = Color(0xFFE3DED5),
    error = Color(0xFFB3261E), onError = Color.White,
)
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
@Composable
fun KraPivaTheme(mode: ThemeMode = ThemeMode.DARK, onThemeChange: (ThemeMode) -> Unit = {}, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThemeMode provides mode, LocalThemeSelection provides onThemeChange) {
        MaterialTheme(colorScheme = if (mode == ThemeMode.DARK) Dark else Light, typography = Type,
            shapes = Shapes(small = ClubTokens.ControlShape, medium = ClubTokens.CardShape, large = RoundedCornerShape(24.dp)), content = content)
    }
}
