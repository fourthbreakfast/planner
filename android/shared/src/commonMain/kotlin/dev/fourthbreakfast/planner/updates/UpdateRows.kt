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
import dev.fourthbreakfast.planner.resources.update_to
import dev.fourthbreakfast.planner.settings.Setting
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The two update lines of the settings: one that shows the version and checks, or offers the newer one and installs
 * it through [install], and one that turns the daily check on and off.
 */
@Composable
fun UpdateRows(updates: Updates, version: String, install: suspend (Release) -> Unit) {
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    var daily by remember { mutableStateOf(updates.daily) }
    val value = when (val now = state) {
        UpdateState.Unknown -> version
        UpdateState.Checking -> stringResource(Res.string.update_checking)
        UpdateState.UpToDate -> stringResource(Res.string.up_to_date) + " · " + version
        is UpdateState.Available -> stringResource(Res.string.update_to, now.release.version)
        UpdateState.Downloading -> stringResource(Res.string.update_downloading)
        UpdateState.Failed -> stringResource(Res.string.update_failed)
    }
    Setting(stringResource(Res.string.setting_updates), value) {
        scope.launch {
            when (val now = state) {
                is UpdateState.Available -> install(now.release)
                UpdateState.Checking, UpdateState.Downloading -> Unit
                else -> updates.check()
            }
        }
    }
    Setting(stringResource(Res.string.update_daily), stringResource(if (daily) Res.string.sync_on else Res.string.backup_off)) {
        daily = !daily
        updates.daily = daily
    }
}
