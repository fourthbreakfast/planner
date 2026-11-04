package dev.fourthbreakfast.planner.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.Repeat
import dev.fourthbreakfast.planner.core.span
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.until_day
import org.jetbrains.compose.resources.stringResource

/**
 * Under an event's name in a list: the day it goes on until, for the time of it that starts on [start], and how it comes
 * back, when it does. A later time of a repeating event ends as long after its own start as the first time did.
 */
@Composable
fun EventSpanAndRepeat(event: Event, start: Long, today: Long) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    if (event.span > 0) {
        Text(stringResource(Res.string.until_day, dateText(start + event.span, today)), style = MaterialTheme.typography.bodyMedium, color = quiet)
    }
    if (event.repeat != Repeat.NONE) {
        Text(repeatText(event.repeat), style = MaterialTheme.typography.bodyMedium, color = quiet)
    }
}
