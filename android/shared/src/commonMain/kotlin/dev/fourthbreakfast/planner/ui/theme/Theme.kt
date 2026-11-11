package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = PageDark,
    primaryContainer = AimDark,
    onPrimaryContainer = InkDark,
    inversePrimary = PageDark,
    error = PriorityHigh,
    onError = Color(0xFF381615),
    secondary = QuietDark,
    onSecondary = PageDark,
    tertiary = QuietDark,
    onTertiary = PageDark,
    tertiaryContainer = AimDark,
    onTertiaryContainer = InkDark,
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
    surfaceContainerLowest = PageDark,
    surfaceContainerLow = PanelDark,
    surfaceContainerHighest = AimDark,
    surfaceBright = PanelDark,
    surfaceDim = PageDark,
    surfaceTint = Color.Transparent,
    outline = ControlOutlineDark,
    outlineVariant = LineDark,
    inverseSurface = InkDark,
    inverseOnSurface = PageDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AimLight,
    onPrimaryContainer = InkLight,
    inversePrimary = PageLight,
    error = ErrorRed,
    onError = Color.White,
    secondary = QuietLight,
    onSecondary = Color.White,
    tertiary = QuietLight,
    onTertiary = Color.White,
    tertiaryContainer = AimLight,
    onTertiaryContainer = InkLight,
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
    surfaceContainerLowest = PageLight,
    surfaceContainerLow = PanelLight,
    surfaceContainerHighest = AimLight,
    surfaceBright = PageLight,
    surfaceDim = PanelLight,
    surfaceTint = Color.Transparent,
    outline = ControlOutlineLight,
    outlineVariant = LineLight,
    inverseSurface = InkLight,
    inverseOnSurface = PageLight,
)

@Composable
fun PlannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), textScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val selection = TextSelectionColors(
        handleColor = colors.onSurface,
        backgroundColor = colors.onSurface.copy(alpha = 0.22f),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = plannerTypography(textScale),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(Space.radius),
            small = RoundedCornerShape(Space.radius),
            medium = RoundedCornerShape(Space.radius),
            large = RoundedCornerShape(Space.radius),
            extraLarge = RoundedCornerShape(Space.radius),
        ),
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selection, LocalContentColor provides colors.onBackground, content = content)
    }
}
