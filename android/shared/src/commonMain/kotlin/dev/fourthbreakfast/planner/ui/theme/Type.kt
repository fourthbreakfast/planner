package dev.fourthbreakfast.planner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.plex_mono_medium
import dev.fourthbreakfast.planner.resources.plex_mono_regular
import dev.fourthbreakfast.planner.resources.plex_sans_bold
import dev.fourthbreakfast.planner.resources.plex_sans_italic
import dev.fourthbreakfast.planner.resources.plex_sans_regular
import org.jetbrains.compose.resources.Font

/** The body font, bundled so the app reads the same on every phone. IBM Plex Sans, under the SIL open font licence (docs/licenses). */
@Composable
fun plex(): FontFamily = FontFamily(
    Font(Res.font.plex_sans_regular, FontWeight.Normal),
    Font(Res.font.plex_sans_bold, FontWeight.Bold),
    Font(Res.font.plex_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The console voice: numbers, small labels and the headings of lists. IBM Plex Mono. */
@Composable
fun plexMono(): FontFamily = FontFamily(
    Font(Res.font.plex_mono_regular, FontWeight.Normal),
    Font(Res.font.plex_mono_medium, FontWeight.Medium),
)

/** The type scale, built once the fonts are loaded. */
@Composable
fun plannerTypography(): Typography {
    val plex = plex()
    val mono = plexMono()
    return Typography(
        displaySmall = TextStyle(fontFamily = plex, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
        headlineSmall = TextStyle(fontFamily = plex, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        // the header and the small numbers and notes use a monospace, like a console
        titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = plex, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = plex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    )
}
