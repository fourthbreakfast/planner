package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PriorityHigh,
    onPrimary = Color.White,
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
    primary = PriorityHigh,
    onPrimary = Color.White,
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
fun PlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    // the text cursor handle and selection follow the page colors, not the red accent
    val selection = TextSelectionColors(
        handleColor = colors.onSurfaceVariant,
        backgroundColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selection, content = content)
    }
}
