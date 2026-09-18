package com.visionaid.app.presentation.theme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// High-contrast palette (spec 11.1: black background, white/yellow text)
// ======================================================================

val VisionAidYellow = Color(0xFFFFD54F)
val VisionAidAmber = Color(0xFFFFB300)
val AlertRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF69F0AE)
val ScreenBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF111111)
val SurfaceVariantDark = Color(0xFF1E1E1E)
val TextWhite = Color(0xFFFFFFFF)

private val HighContrastColorScheme = darkColorScheme(
    primary = VisionAidYellow,
    onPrimary = Color.Black,
    secondary = SuccessGreen,
    onSecondary = Color.Black,
    tertiary = VisionAidAmber,
    onTertiary = Color.Black,
    error = AlertRed,
    onError = Color.Black,
    background = ScreenBlack,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = VisionAidYellow
)

// ======================================================================
// Large, readable typography (spec 11.1)
// ======================================================================

val AccessibleTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    bodySmall = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp)
)

/**
 * The app theme: always high-contrast dark, regardless of system setting —
 * maximum legibility is a core requirement for this user group.
 */
@Composable
fun VisionAidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColorScheme,
        typography = AccessibleTypography,
        content = content
    )
}

/**
 * Reusable large touch target (spec 11.1: minimum 64x64dp, we use 96dp
 * height). Every instance exposes a contentDescription for TalkBack.
 */
@Composable
fun LargeAccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.Black,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
