package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.fira_code_bold
import dev.fourthbreakfast.planner.resources.fira_code_medium
import dev.fourthbreakfast.planner.resources.fira_code_regular
import org.jetbrains.compose.resources.Font

/** An explicit programming face for notes that request ligatures; ordinary interface text stays proportional. */
@Composable
fun firaCode(): FontFamily = FontFamily(
    Font(Res.font.fira_code_regular, FontWeight.Normal),
    Font(Res.font.fira_code_medium, FontWeight.Medium),
    Font(Res.font.fira_code_bold, FontWeight.Bold),
)

/** Page names and task content share a compact 16sp scale. User font scaling remains platform controlled. */
@Composable
fun plannerTypography(scale: Float = 1f): Typography {
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )
    val page = style(16, 24, FontWeight.Medium)
    return Typography(
        displayLarge = page,
        displayMedium = page,
        displaySmall = page,
        headlineLarge = page,
        headlineMedium = page,
        headlineSmall = page,
        titleLarge = page,
        titleMedium = page,
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24),
        bodyMedium = style(15, 22),
        bodySmall = style(13, 20),
        labelLarge = style(15, 22),
        labelMedium = style(13, 20),
        labelSmall = style(12, 18),
    )
}
