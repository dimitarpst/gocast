package app.gocast.ui.theme

import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.gocast.R

// These exact font files are also packaged by the desktop app.
private val GocastFont = FontFamily(
    Font(R.font.gocast_regular, FontWeight.Normal),
    Font(R.font.gocast_medium, FontWeight.Medium),
    Font(R.font.gocast_medium, FontWeight.SemiBold),
    Font(R.font.gocast_bold, FontWeight.Bold),
)

val Typography = MaterialTypography().run { copy(
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
