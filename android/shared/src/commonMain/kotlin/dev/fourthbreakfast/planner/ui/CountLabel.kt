package dev.fourthbreakfast.planner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Quiet, legible metadata that scales with the user's text size. */
@Composable
fun CountLabel(value: Int, color: Color, modifier: Modifier = Modifier) {
    Text(value.coerceAtLeast(0).toString(), color = color, style = MaterialTheme.typography.labelMedium, modifier = modifier)
}
