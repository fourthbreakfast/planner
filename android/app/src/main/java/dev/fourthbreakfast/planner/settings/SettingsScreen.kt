package dev.fourthbreakfast.planner.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.ThemeChoice
import dev.fourthbreakfast.planner.backup.folderName
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.tasks.rememberBackupFiles
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.ui.theme.Space
import dev.fourthbreakfast.planner.updates.UpdateRows
import dev.fourthbreakfast.planner.updates.installOnPhone
import dev.fourthbreakfast.planner.widget.PlannerWidget

/** What can be set, the backup, and a list of every gesture. Reached by holding any of the three tabs. */
@Composable
fun SettingsScreen(viewModel: TasksViewModel, onClose: () -> Unit) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    if (guideOpen) {
        Guide(onClose = { guideOpen = false })
        return
    }
    if (syncOpen) {
        val controller = (LocalContext.current.applicationContext as PlannerApp).syncController
        var onCode by remember { mutableStateOf<((String) -> Unit)?>(null) }
        val camera = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { onCode?.invoke(it) } }
        SyncScreen(controller, scan = { then ->
            onCode = then
            camera.launch(ScanOptions().setPrompt("").setBeepEnabled(false).setOrientationLocked(true).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
        }, onClose = { syncOpen = false })
        return
    }
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val settings = (context.applicationContext as PlannerApp).settings
    val snackbarHost = remember { SnackbarHostState() }
    val backup = rememberBackupFiles(viewModel) { snackbarHost.showSnackbar(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder: Uri? ->
        if (folder != null) {
            context.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.chooseBackupFolder(folder.toString())
            (context.applicationContext as PlannerApp).backUpIfDue()
        }
    }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHost) { data -> DiscreetMessage(data.visuals.message, data.visuals.actionLabel, data::performAction) }
        },
        topBar = { SettingsBar(stringResource(R.string.settings_title), onClose) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val sync = (context.applicationContext as PlannerApp).syncController
            if (sync.available) {
                val peers = sync.state.value.peers.size
                Setting(
                    stringResource(R.string.sync_title),
                    if (peers ==
                        0
                    ) {
                        null
                    } else {
                        pluralStringResource(R.plurals.sync_devices, peers, peers)
                    },
                ) { syncOpen = true }
            }
            Setting(stringResource(R.string.export_tasks), null, backup.export)
            Setting(stringResource(R.string.import_tasks), null, backup.import)
            Setting(
                label = stringResource(R.string.setting_toolbar),
                value = stringResource(if (settings.toolbarAbove) R.string.toolbar_keyboard else R.string.toolbar_top),
            ) { settings.chooseToolbarAbove(!settings.toolbarAbove) }
            Setting(
                label = stringResource(R.string.setting_theme),
                value = stringResource(
                    when (settings.theme) {
                        ThemeChoice.LIGHT -> R.string.theme_light
                        ThemeChoice.DARK -> R.string.theme_dark
                        ThemeChoice.SYSTEM -> R.string.theme_system
                    },
                ),
            ) { settings.chooseTheme(ThemeChoice.entries[(settings.theme.ordinal + 1) % ThemeChoice.entries.size]) }
            Setting(
                label = stringResource(R.string.setting_backup),
                value = settings.backupFolder?.let { folderName(it) } ?: stringResource(R.string.backup_off),
            ) {
                if (settings.backupFolder != null) settings.chooseBackupFolder(null) else pickFolder.launch(null)
            }
            val app = context.applicationContext as PlannerApp
            UpdateRows(app.updates, version) { ready -> installOnPhone(context, ready) }
            val manager = remember { AppWidgetManager.getInstance(context) }
            if (manager.isRequestPinAppWidgetSupported) {
                Setting(stringResource(R.string.setting_widget), null) {
                    manager.requestPinAppWidget(ComponentName(context, PlannerWidget::class.java), null, null)
                }
            }
            Setting(stringResource(R.string.guide_title), null) { guideOpen = true }
            Text(
                text = stringResource(R.string.app_name) + " " + version,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.gutter, top = Space.x6),
            )
        }
    }
}
