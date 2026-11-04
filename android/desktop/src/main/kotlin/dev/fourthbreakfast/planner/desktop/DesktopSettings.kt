package dev.fourthbreakfast.planner.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fourthbreakfast.planner.ThemeChoice
import dev.fourthbreakfast.planner.core.BackupException
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.app_name
import dev.fourthbreakfast.planner.resources.backup_off
import dev.fourthbreakfast.planner.resources.calendar_done
import dev.fourthbreakfast.planner.resources.desktop_guide
import dev.fourthbreakfast.planner.resources.export_done
import dev.fourthbreakfast.planner.resources.export_failed
import dev.fourthbreakfast.planner.resources.export_tasks
import dev.fourthbreakfast.planner.resources.guide_title
import dev.fourthbreakfast.planner.resources.import_calendar
import dev.fourthbreakfast.planner.resources.import_done
import dev.fourthbreakfast.planner.resources.import_failed
import dev.fourthbreakfast.planner.resources.import_nothing_new
import dev.fourthbreakfast.planner.resources.import_tasks
import dev.fourthbreakfast.planner.resources.setting_theme
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.resources.start_with_windows
import dev.fourthbreakfast.planner.resources.sync_devices
import dev.fourthbreakfast.planner.resources.sync_failed_desktop
import dev.fourthbreakfast.planner.resources.sync_on
import dev.fourthbreakfast.planner.resources.sync_title
import dev.fourthbreakfast.planner.resources.theme_dark
import dev.fourthbreakfast.planner.resources.theme_light
import dev.fourthbreakfast.planner.resources.theme_system
import dev.fourthbreakfast.planner.settings.Guide
import dev.fourthbreakfast.planner.settings.Setting
import dev.fourthbreakfast.planner.settings.SettingsBar
import dev.fourthbreakfast.planner.settings.SyncScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.LocalServices
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.updates.UpdateRows
import dev.fourthbreakfast.planner.updates.UpdateState
import dev.fourthbreakfast.planner.updates.Updates
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val FILE_NAME = "planner-backup.json"

/** The computer's settings: sync, a backup to a file and back, the theme, and the list of gestures. */
@Composable
fun DesktopSettings(
    viewModel: TasksViewModel,
    sync: DesktopSync,
    updates: Updates,
    version: String,
    onClose: () -> Unit,
    install: (UpdateState.Ready) -> Unit,
) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
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
    val state by sync.state.collectAsState()

    fun export() {
        val file = chooseFile(FileDialog.SAVE) ?: return
        scope.launch {
            val message = try {
                val text = viewModel.exportBackup()
                withContext(Dispatchers.IO) { file.writeText(text) }
                getString(Res.string.export_done)
            } catch (_: IOException) {
                getString(Res.string.export_failed)
            }
            snackbarHost.showSnackbar(message)
        }
    }

    fun import() {
        val file = chooseFile(FileDialog.LOAD) ?: return
        scope.launch {
            val message = try {
                val added = viewModel.importBackup(withContext(Dispatchers.IO) { file.readText() })
                if (added == 0) getString(Res.string.import_nothing_new) else getPluralString(Res.plurals.import_done, added, added)
            } catch (_: BackupException) {
                getString(Res.string.import_failed)
            } catch (_: IOException) {
                getString(Res.string.import_failed)
            }
            snackbarHost.showSnackbar(message)
        }
    }

    fun importCalendar() {
        val file = chooseFile(FileDialog.LOAD) ?: return
        scope.launch {
            val message = try {
                val added = viewModel.importCalendar(withContext(Dispatchers.IO) { file.readText() })
                if (added == 0) getString(Res.string.import_nothing_new) else getPluralString(Res.plurals.calendar_done, added, added)
            } catch (_: BackupException) {
                getString(Res.string.import_failed)
            } catch (_: IOException) {
                getString(Res.string.import_failed)
            }
            snackbarHost.showSnackbar(message)
        }
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
            if (sync.available) {
                val peers = state.peers.size
                val count = if (peers == 0) null else pluralStringResource(Res.plurals.sync_devices, peers, peers)
                Setting(stringResource(Res.string.sync_title), count) { syncOpen = true }
            }
            Setting(stringResource(Res.string.export_tasks), null, ::export)
            Setting(stringResource(Res.string.import_tasks), null, ::import)
            Setting(stringResource(Res.string.import_calendar), null, ::importCalendar)
            Setting(
                label = stringResource(Res.string.setting_theme),
                value = stringResource(
                    when (settings.theme) {
                        ThemeChoice.LIGHT -> Res.string.theme_light
                        ThemeChoice.DARK -> Res.string.theme_dark
                        ThemeChoice.SYSTEM -> Res.string.theme_system
                    },
                ),
            ) { settings.chooseTheme(ThemeChoice.entries[(settings.theme.ordinal + 1) % ThemeChoice.entries.size]) }
            if (StartWithWindows.possible) {
                var startOn by remember { mutableStateOf(StartWithWindows.on) }
                Setting(stringResource(Res.string.start_with_windows), stringResource(if (startOn) Res.string.sync_on else Res.string.backup_off)) {
                    StartWithWindows.set(!startOn)
                    startOn = StartWithWindows.on
                }
            }
            UpdateRows(updates, version, install)
            Setting(stringResource(Res.string.guide_title), null) { guideOpen = true }
            Text(
                text = stringResource(Res.string.app_name) + " " + version,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.gutter, top = Space.x6),
            )
        }
    }
}

/** The system's own file chooser, to save or to open a backup. */
private fun chooseFile(mode: Int): File? {
    val dialog = FileDialog(null as Frame?, "planner", mode)
    if (mode == FileDialog.SAVE) dialog.file = FILE_NAME
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
