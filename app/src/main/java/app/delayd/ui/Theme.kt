package app.delayd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spacing scale, dp. */
object Space {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
}

/** Colours M3 has no slot for ("paid" green). */
@Immutable
class ExtraColors(val success: Color, val successContainer: Color, val onSuccessContainer: Color)

val LocalExtra = staticCompositionLocalOf { LightExtra }
private val LightExtra = ExtraColors(Color(0xFF1B6B2F), Color(0xFFCFEFD3), Color(0xFF0A3A17))
private val DarkExtra = ExtraColors(Color(0xFF8FD99B), Color(0xFF1F4D2B), Color(0xFFCFEFD3))

private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6B5D), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBEBE2), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF3F5B73), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F2), onSecondaryContainer = Color(0xFF17293A),
    tertiary = Color(0xFF7A4E00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE6BE), onTertiaryContainer = Color(0xFF3D2600),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F8F7), onBackground = Color(0xFF17211E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17211E),
    surfaceVariant = Color(0xFFE3EAE7), onSurfaceVariant = Color(0xFF4A5853),
    outline = Color(0xFF6F7B76), outlineVariant = Color(0xFFC8D2CE),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFEBF0EE), surfaceContainerHigh = Color(0xFFE5EBE8), surfaceContainerHighest = Color(0xFFE3EAE7),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF6FD3BE), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF00513F), onPrimaryContainer = Color(0xFFBDEFE2),
    secondary = Color(0xFFA9C4DE), onSecondary = Color(0xFF133247),
    secondaryContainer = Color(0xFF2F4254), onSecondaryContainer = Color(0xFFD6E4F2),
    tertiary = Color(0xFFF1BE6A), onTertiary = Color(0xFF412B00),
    tertiaryContainer = Color(0xFF5C3B00), onTertiaryContainer = Color(0xFFFBE6BE),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0D1311), onBackground = Color(0xFFE0E6E3),
    surface = Color(0xFF131B19), onSurface = Color(0xFFE0E6E3),
    surfaceVariant = Color(0xFF2A3532), onSurfaceVariant = Color(0xFFB2BEB9),
    outline = Color(0xFF85918C), outlineVariant = Color(0xFF3B4743),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF17201E),
    surfaceContainer = Color(0xFF1B2523), surfaceContainerHigh = Color(0xFF222D2A), surfaceContainerHighest = Color(0xFF2A3532),
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val Shape = Shapes(
    small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Dynamic colour stays off so the brand is the same on every phone. */
@Composable
fun DelaydTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
    }
}
