package dev.fourthbreakfast.planner.agenda

import android.text.format.DateFormat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.Duration
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.fourthbreakfast.planner.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun today(): Long = LocalDate.now().toEpochDay()

/** Today, kept right: it changes at midnight and when the app comes back to the front, so nothing shows yesterday. */
@Composable
fun rememberToday(): Long {
    var day by remember { mutableLongStateOf(today()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { day = today() }
    LaunchedEffect(day) {
        val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
        delay(untilMidnight.toMillis() + 500)
        day = today()
    }
    return day
}

/** "sat 31 oct", with the year added when it is not this one. Always lowercase, like everything the app says. */
fun dateText(day: Long, today: Long): String {
    val date = LocalDate.ofEpochDay(day)
    val sameYear = date.year == LocalDate.ofEpochDay(today).year
    val pattern = if (sameYear) "EEE d MMM" else "EEE d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date).lowercase(Locale.getDefault())
}

/** "today · sat 31 oct" for the days around now, the plain date for the rest. */
@Composable
fun dayText(day: Long, today: Long): String {
    val date = dateText(day, today)
    return when (day - today) {
        0L -> stringResource(R.string.day_today) + " · " + date
        1L -> stringResource(R.string.day_tomorrow) + " · " + date
        -1L -> stringResource(R.string.day_yesterday) + " · " + date
        else -> date
    }
}

@Composable
fun rememberIs24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

/** "19:30", or "7:30 pm" when the phone uses a twelve hour clock. */
fun timeText(minute: Int, is24Hour: Boolean): String {
    val hour = minute / 60
    val mins = minute % 60
    return if (is24Hour) {
        "%02d:%02d".format(hour, mins)
    } else {
        "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, mins, if (hour < 12) "am" else "pm")
    }
}
