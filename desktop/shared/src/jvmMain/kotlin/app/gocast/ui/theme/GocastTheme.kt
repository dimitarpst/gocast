package app.gocast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp

// The same Roboto resources and corner scale are used by the phone app.
private val GocastFont = FontFamily(
    Font("gocast_regular.ttf", FontWeight.Normal),
    Font("gocast_medium.ttf", FontWeight.Medium),
    Font("gocast_medium.ttf", FontWeight.SemiBold),
    Font("gocast_bold.ttf", FontWeight.Bold),
)
private val GocastTypography = Typography().run { copy(
    displayLarge = displayLarge.copy(fontFamily = GocastFont),
    displayMedium = displayMedium.copy(fontFamily = GocastFont),
    displaySmall = displaySmall.copy(fontFamily = GocastFont),
    headlineLarge = headlineLarge.copy(fontFamily = GocastFont),
    headlineMedium = headlineMedium.copy(fontFamily = GocastFont),
    headlineSmall = headlineSmall.copy(fontFamily = GocastFont),
    titleLarge = titleLarge.copy(fontFamily = GocastFont),
    titleMedium = titleMedium.copy(fontFamily = GocastFont),
    titleSmall = titleSmall.copy(fontFamily = GocastFont),
    bodyLarge = bodyLarge.copy(fontFamily = GocastFont),
    bodyMedium = bodyMedium.copy(fontFamily = GocastFont),
    bodySmall = bodySmall.copy(fontFamily = GocastFont),
    labelLarge = labelLarge.copy(fontFamily = GocastFont),
    labelMedium = labelMedium.copy(fontFamily = GocastFont),
    labelSmall = labelSmall.copy(fontFamily = GocastFont),
) }
private val GocastShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
private val GocastColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF101318),
    background = Color(0xFF12141A),
    surface = Color(0xFF1B2030),
    onBackground = Color(0xFFE8ECF5),
    onSurface = Color(0xFFE8ECF5),
    onSurfaceVariant = Color(0xFF9AA3B5),
)

@Composable
fun GocastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GocastColors,
        typography = GocastTypography,
        shapes = GocastShapes,
        content = content,
    )
}
