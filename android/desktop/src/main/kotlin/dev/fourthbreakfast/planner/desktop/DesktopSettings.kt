package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fourthbreakfast.planner.calendar.CalendarSubscriptionsScreen
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.CalendarSubscriptions
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.calendar_subscriptions
import dev.fourthbreakfast.planner.resources.desktop_guide
import dev.fourthbreakfast.planner.resources.export_tasks
import dev.fourthbreakfast.planner.resources.guide_title
import dev.fourthbreakfast.planner.resources.import_calendar
import dev.fourthbreakfast.planner.resources.import_tasks
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.resources.start_with_windows
import dev.fourthbreakfast.planner.resources.sync_failed_desktop
import dev.fourthbreakfast.planner.settings.BACKUP_FILE_NAME
import dev.fourthbreakfast.planner.settings.Guide
import dev.fourthbreakfast.planner.settings.Setting
import dev.fourthbreakfast.planner.settings.SettingsBar
import dev.fourthbreakfast.planner.settings.SyncScreen
import dev.fourthbreakfast.planner.settings.SyncSetting
import dev.fourthbreakfast.planner.settings.ThemeSetting
import dev.fourthbreakfast.planner.settings.VersionLine
import dev.fourthbreakfast.planner.settings.exportBackup
import dev.fourthbreakfast.planner.settings.importFile
import dev.fourthbreakfast.planner.settings.onOff
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.updates.UpdateRows
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The computer's settings: sync, a backup to a file and back, the theme, updates, and the list of shortcuts. */
@Composable
fun DesktopSettings(
    backups: Backups,
    sync: DesktopSync,
    updates: Updates,
    version: String,
    onClose: () -> Unit,
    install: (UpdateState.Ready) -> Unit,
    calendars: CalendarSubscriptions? = null,
) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    var calendarsOpen by rememberSaveable { mutableStateOf(false) }
    if (calendarsOpen && calendars != null) {
        CalendarSubscriptionsScreen(calendars) { calendarsOpen = false }
        return
    }
    if (guideOpen) {
        Guide(Res.array.desktop_guide, onClose = { guideOpen = false })
        return
    }
    if (syncOpen) {
        // a computer has no camera to scan with: it shows its own code for the phone to scan
        SyncScreen(sync, scan = null, failed = stringResource(Res.string.sync_failed_desktop), onClose = { syncOpen = false })
        return
    }
    BackHandler(onBack = onClose)
    val settings = LocalServices.current.settings
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun export() {
        val file = chooseFile(FileDialog.SAVE) ?: return
        scope.launch { snackbarHost.showSnackbar(exportBackup(backups) { withContext(Dispatchers.IO) { file.writeText(it) } }) }
    }

    fun import(calendar: Boolean) {
        val file = chooseFile(FileDialog.LOAD) ?: return
        scope.launch { snackbarHost.showSnackbar(importFile(backups, calendar) { withContext(Dispatchers.IO) { file.readText() } }) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data -> DiscreetMessage(data.visuals.message, data.visuals.actionLabel, data::performAction) }
        },
        topBar = { SettingsBar(stringResource(Res.string.settings_title), onClose) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SyncSetting(sync) { syncOpen = true }
            Setting(stringResource(Res.string.export_tasks), null, ::export)
            Setting(stringResource(Res.string.import_tasks), null) { import(calendar = false) }
            Setting(stringResource(Res.string.import_calendar), null) { import(calendar = true) }
            if (calendars != null) Setting(stringResource(Res.string.calendar_subscriptions), null) { calendarsOpen = true }
            ThemeSetting(settings)
            if (StartWithWindows.possible) {
                var startOn by remember { mutableStateOf(StartWithWindows.on) }
                Setting(stringResource(Res.string.start_with_windows), onOff(startOn)) {
                    StartWithWindows.set(!startOn)
                    startOn = StartWithWindows.on
                }
            }
            UpdateRows(updates, version, install)
            Setting(stringResource(Res.string.guide_title), null) { guideOpen = true }
            VersionLine(version)
        }
    }
}

/** The system's own file chooser, to save or to open a backup. */
private fun chooseFile(mode: Int): File? {
    val dialog = FileDialog(null as Frame?, "planner", mode)
    if (mode == FileDialog.SAVE) dialog.file = BACKUP_FILE_NAME
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
