package de.flexy.widgetwerk.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Google Sans vom Pixel (fällt auf Roboto zurück, falls nicht vorhanden). */
val GoogleSans: FontFamily = FontFamily(Typeface.create("google-sans", Typeface.NORMAL))

private val base = Typography()
private val WerkTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    displayMedium = base.displayMedium.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    displaySmall = base.displaySmall.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    headlineLarge = base.headlineLarge.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = GoogleSans, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = GoogleSans, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = GoogleSans, fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontFamily = GoogleSans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = GoogleSans),
    bodyLarge = base.bodyLarge.copy(fontFamily = GoogleSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = GoogleSans),
)

@Composable
fun WidgetwerkTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = WerkTypography,
        content = content,
    )
}
