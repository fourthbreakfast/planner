package dev.fourthbreakfast.planner.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.core.CalendarFailure
import dev.fourthbreakfast.planner.core.CalendarFeedException
import dev.fourthbreakfast.planner.core.CalendarSubscription
import dev.fourthbreakfast.planner.core.CalendarSubscriptions
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.back
import dev.fourthbreakfast.planner.resources.calendar_add
import dev.fourthbreakfast.planner.resources.calendar_empty
import dev.fourthbreakfast.planner.resources.calendar_error_auth
import dev.fourthbreakfast.planner.resources.calendar_error_invalid
import dev.fourthbreakfast.planner.resources.calendar_error_large
import dev.fourthbreakfast.planner.resources.calendar_error_network
import dev.fourthbreakfast.planner.resources.calendar_error_server
import dev.fourthbreakfast.planner.resources.calendar_error_unsupported
import dev.fourthbreakfast.planner.resources.calendar_error_url
import dev.fourthbreakfast.planner.resources.calendar_https_hint
import dev.fourthbreakfast.planner.resources.calendar_last_updated
import dev.fourthbreakfast.planner.resources.calendar_name
import dev.fourthbreakfast.planner.resources.calendar_never
import dev.fourthbreakfast.planner.resources.calendar_privacy_hint
import dev.fourthbreakfast.planner.resources.calendar_refresh
import dev.fourthbreakfast.planner.resources.calendar_refreshing
import dev.fourthbreakfast.planner.resources.calendar_remove
import dev.fourthbreakfast.planner.resources.calendar_remove_message
import dev.fourthbreakfast.planner.resources.calendar_remove_title
import dev.fourthbreakfast.planner.resources.calendar_source_remote
import dev.fourthbreakfast.planner.resources.calendar_subscriptions
import dev.fourthbreakfast.planner.resources.calendar_url
import dev.fourthbreakfast.planner.resources.calendar_window_hint
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.ScreenHeading
import dev.fourthbreakfast.planner.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Local feed setup, with remote calendars still visible for safe, source-wide removal. */
@Composable
fun CalendarSubscriptionsScreen(calendars: CalendarSubscriptions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val subscriptions by calendars.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val refreshing by calendars.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CalendarSubscription?>(null) }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.rowPad), verticalAlignment = Alignment.CenterVertically) {
                ScreenHeading(stringResource(Res.string.calendar_subscriptions), Modifier.weight(1f))
                BackButton(onClose)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(Space.gutter)) {
                    Text(stringResource(Res.string.calendar_https_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.calendar_window_hint), Modifier.padding(top = Space.x2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.calendar_privacy_hint), Modifier.padding(top = Space.x2), style = MaterialTheme.typography.bodySmall)
                    QuietButton(stringResource(Res.string.calendar_add)) { adding = true }
                }
            }
            if (subscriptions.isEmpty()) {
                item {
                    Text(stringResource(Res.string.calendar_empty), Modifier.padding(Space.gutter), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(subscriptions, key = CalendarSubscription::id) { source ->
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.x3)) {
                    Text(source.name, style = MaterialTheme.typography.bodyLarge)
                    val lastSuccess = source.lastSuccess
                    val status = when {
                        source.id in refreshing -> stringResource(Res.string.calendar_refreshing)
                        source.url.isEmpty() -> stringResource(Res.string.calendar_source_remote)
                        lastSuccess != null -> stringResource(
                            Res.string.calendar_last_updated,
                            DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(lastSuccess).atZone(ZoneId.systemDefault())),
                        )
                        else -> stringResource(Res.string.calendar_never)
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    source.failure?.let { Text(calendarFailureText(it), style = MaterialTheme.typography.bodySmall, color = colors.error) }
                    Row {
                        if (source.url.isNotEmpty()) {
                            QuietButton(stringResource(Res.string.calendar_refresh), source.id !in refreshing) {
                                scope.launch { calendars.refresh(source.id) }
                            }
                        }
                        QuietButton(stringResource(Res.string.calendar_remove), source.id !in refreshing) { removing = source }
                    }
                }
            }
        }
    }
    if (adding) {
        AddCalendarDialog(onDismiss = { adding = false }) { name, url, onError ->
            scope.launch {
                try {
                    val id = calendars.subscribe(name, url)
                    adding = false
                    calendars.refresh(id)
                } catch (e: CalendarFeedException) {
                    onError(e.reason)
                }
            }
        }
    }
    removing?.let { source ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.calendar_remove_title)) },
            text = { Text(stringResource(Res.string.calendar_remove_message)) },
            confirmButton = {
                QuietButton(stringResource(Res.string.calendar_remove)) {
                    removing = null
                    scope.launch { calendars.remove(source.id) }
                }
            },
            dismissButton = { QuietButton(stringResource(Res.string.back)) { removing = null } },
        )
    }
}

@Composable
private fun AddCalendarDialog(onDismiss: () -> Unit, onAdd: (String, String, (CalendarFailure) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<CalendarFailure?>(null) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.calendar_add)) },
        text = {
            Column {
                TextField(name, { name = it }, label = { Text(stringResource(Res.string.calendar_name)) }, singleLine = true)
                TextField(
                    url,
                    {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.calendar_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                error?.let { Text(calendarFailureText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            QuietButton(stringResource(Res.string.calendar_add), url.isNotBlank() && !adding) {
                adding = true
                onAdd(name, url) { failure ->
                    error = failure
                    adding = false
                }
            }
        },
        dismissButton = { QuietButton(stringResource(Res.string.back), onDismiss) },
    )
}

@Composable
private fun calendarFailureText(failure: CalendarFailure): String = stringResource(
    when (failure) {
        CalendarFailure.INVALID_URL -> Res.string.calendar_error_url
        CalendarFailure.INVALID_FEED -> Res.string.calendar_error_invalid
        CalendarFailure.UNSUPPORTED_FEED -> Res.string.calendar_error_unsupported
        CalendarFailure.TOO_LARGE -> Res.string.calendar_error_large
        CalendarFailure.NETWORK -> Res.string.calendar_error_network
        CalendarFailure.AUTHENTICATION -> Res.string.calendar_error_auth
        CalendarFailure.SERVER -> Res.string.calendar_error_server
    },
)
