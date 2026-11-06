package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = PageDark,
    error = ErrorRed,
    onError = Color.White,
    secondaryContainer = AimDark,
    onSecondaryContainer = InkDark,
    background = PageDark,
    onBackground = InkDark,
    surface = PageDark,
    onSurface = InkDark,
    surfaceVariant = PanelDark,
    onSurfaceVariant = QuietDark,
    surfaceContainer = PanelDark,
    surfaceContainerHigh = PanelDark,
    outline = LineDark,
    inverseSurface = InkDark,
    inverseOnSurface = PageDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    secondaryContainer = AimLight,
    onSecondaryContainer = InkLight,
    background = PageLight,
    onBackground = InkLight,
    surface = PageLight,
    onSurface = InkLight,
    surfaceVariant = PanelLight,
    onSurfaceVariant = QuietLight,
    surfaceContainer = PanelLight,
    surfaceContainerHigh = PanelLight,
    outline = LineLight,
    inverseSurface = InkLight,
    inverseOnSurface = PageLight,
)

@Composable
fun PlannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), textScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    // Selection follows the quiet ink of the page.
    val selection = TextSelectionColors(
        handleColor = colors.onSurfaceVariant,
        backgroundColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = plannerTypography(textScale),
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selection, content = content)
    }
}
