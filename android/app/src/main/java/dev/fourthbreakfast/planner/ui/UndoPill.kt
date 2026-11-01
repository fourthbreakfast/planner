package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the undo pill stays, long enough to reach it with a thumb. */
private const val UNDO_MILLIS = 6000L

/** Shows [message] with an undo button and takes it away after a while. True when undo was touched. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean = coroutineScope {
    currentSnackbarData?.dismiss()
    val timer = launch {
        delay(UNDO_MILLIS)
        currentSnackbarData?.dismiss()
    }
    val result = showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    timer.cancel()
    result == SnackbarResult.ActionPerformed
}

/** A small pill: says what happened and offers the way back, without covering the list. */
@Composable
fun DiscreetMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().padding(bottom = Space.x1), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                .padding(start = Space.gutter, end = if (action == null) Space.gutter else Space.rowPad)
                .then(if (action == null) Modifier.padding(vertical = Space.x3) else Modifier),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) QuietButton(action, onAction)
        }
    }
}
