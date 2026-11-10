package app.okusurikiroku.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F3EA),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF456B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEFEA),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3BC),
    onTertiaryContainer = Color(0xFF2F1A00),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF151D1B),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF151D1B),
    surfaceVariant = Color(0xFFE3EAE7),
    onSurfaceVariant = Color(0xFF55625E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFA),
    surfaceContainer = Color(0xFFEEF2F0),
    surfaceContainerHigh = Color(0xFFE8EDEB),
    surfaceContainerHighest = Color(0xFFE1E7E5),
    outline = Color(0xFF7A8783),
    outlineVariant = Color(0xFFD5DDDA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDCC6),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0E4A40),
    onPrimaryContainer = Color(0xFFBDF3E6),
    secondary = Color(0xFFA9CFC6),
    onSecondary = Color(0xFF13352F),
    secondaryContainer = Color(0xFF263F3A),
    onSecondaryContainer = Color(0xFFC6EBE2),
    tertiary = Color(0xFFFFC470),
    onTertiary = Color(0xFF4A2C00),
    tertiaryContainer = Color(0xFF5E3F00),
    onTertiaryContainer = Color(0xFFFFE3BC),
    background = Color(0xFF0D1412),
    onBackground = Color(0xFFE0E6E3),
    surface = Color(0xFF0D1412),
    onSurface = Color(0xFFE0E6E3),
    surfaceVariant = Color(0xFF2A3431),
    onSurfaceVariant = Color(0xFFB4C0BC),
    surfaceContainerLowest = Color(0xFF09100E),
    surfaceContainerLow = Color(0xFF141C1A),
    surfaceContainer = Color(0xFF18211E),
    surfaceContainerHigh = Color(0xFF212B28),
    surfaceContainerHighest = Color(0xFF2B3633),
    outline = Color(0xFF8A9793),
    outlineVariant = Color(0xFF34403C),
)

/** Material の配色に加えて使う、このアプリ独自の色。 */
@Immutable
data class BrandColors(
    /** ヘッダーカードのグラデーション。 */
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    /** カードの地の色と影の色。 */
    val card: Color,
    val cardShadow: Color,
    /** お祝いの紙吹雪。 */
    val confetti: List<Color>,
    val isDark: Boolean,
)

private val Confetti = listOf(
    Color(0xFF2BB89E),
    Color(0xFFFFC857),
    Color(0xFFFF7A59),
    Color(0xFF6FB7FF),
    Color(0xFFB79CED),
    Color(0xFFFF9EC4),
)

private val LightBrand = BrandColors(
    heroStart = Color(0xFF0A6658),
    heroEnd = Color(0xFF22B095),
    onHero = Color.White,
    card = Color.White,
    cardShadow = Color(0xFF0A6658),
    confetti = Confetti,
    isDark = false,
)

private val DarkBrand = BrandColors(
    heroStart = Color(0xFF0B4A40),
    heroEnd = Color(0xFF138A75),
    onHero = Color(0xFFF1FFFB),
    card = Color(0xFF18211E),
    cardShadow = Color.Black,
    confetti = Confetti,
    isDark = true,
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * [dynamicColor] を true にすると、Android 12 以降で壁紙に合わせた配色を使う。
 * 既定では、ヘッダーのグラデーションなどと色がそろうよう、アプリの配色を使う。
 */
@Composable
fun OkusuriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalBrand provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
