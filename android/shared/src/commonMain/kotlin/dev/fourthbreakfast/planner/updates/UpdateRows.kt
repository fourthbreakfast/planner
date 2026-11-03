package dev.fourthbreakfast.planner.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.backup_off
import dev.fourthbreakfast.planner.resources.setting_updates
import dev.fourthbreakfast.planner.resources.sync_on
import dev.fourthbreakfast.planner.resources.up_to_date
import dev.fourthbreakfast.planner.resources.update_checking
import dev.fourthbreakfast.planner.resources.update_daily
import dev.fourthbreakfast.planner.resources.update_downloading
import dev.fourthbreakfast.planner.resources.update_failed
import dev.fourthbreakfast.planner.resources.update_ready
import dev.fourthbreakfast.planner.settings.Setting
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The two update lines of the settings: one that shows the version and checks, or offers the downloaded newer one and
 * installs it through [install], and one that turns the daily check on and off.
 */
@Composable
fun UpdateRows(updates: Updates, version: String, install: (UpdateState.Ready) -> Unit) {
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    var daily by remember { mutableStateOf(updates.daily) }
    val value = when (val now = state) {
        UpdateState.Unknown -> version
        UpdateState.Checking -> stringResource(Res.string.update_checking)
        UpdateState.UpToDate -> stringResource(Res.string.up_to_date) + " · " + version
        is UpdateState.Downloading -> stringResource(Res.string.update_downloading)
        is UpdateState.Ready -> stringResource(Res.string.update_ready, now.release.version)
        UpdateState.Failed -> stringResource(Res.string.update_failed)
    }
    Setting(stringResource(Res.string.setting_updates), value) {
        when (val now = state) {
            is UpdateState.Ready -> install(now)
            UpdateState.Checking, is UpdateState.Downloading -> Unit
            else -> scope.launch { updates.check() }
        }
    }
    Setting(stringResource(Res.string.update_daily), stringResource(if (daily) Res.string.sync_on else Res.string.backup_off)) {
        daily = !daily
        updates.daily = daily
    }
}
