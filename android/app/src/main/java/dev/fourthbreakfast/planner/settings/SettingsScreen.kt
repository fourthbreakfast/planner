package dev.fourthbreakfast.planner.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.backup.folderName
import dev.fourthbreakfast.planner.backup.rememberBackupFiles
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.export_tasks
import dev.fourthbreakfast.planner.resources.guide_title
import dev.fourthbreakfast.planner.resources.import_calendar
import dev.fourthbreakfast.planner.resources.import_tasks
import dev.fourthbreakfast.planner.resources.off
import dev.fourthbreakfast.planner.resources.setting_backup
import dev.fourthbreakfast.planner.resources.setting_toolbar
import dev.fourthbreakfast.planner.resources.setting_widget
import dev.fourthbreakfast.planner.resources.settings_title
import dev.fourthbreakfast.planner.resources.sync_blocked
import dev.fourthbreakfast.planner.resources.sync_failed_phone
import dev.fourthbreakfast.planner.resources.toolbar_keyboard
import dev.fourthbreakfast.planner.resources.toolbar_top
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.DiscreetMessage
import dev.fourthbreakfast.planner.updates.UpdateRows
import dev.fourthbreakfast.planner.updates.installOnPhone
import dev.fourthbreakfast.planner.widget.PlannerWidget
import org.jetbrains.compose.resources.stringResource

/** What can be set, the backup, and a list of every gesture. Reached by holding any of the three tabs. */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PlannerApp
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    if (guideOpen) {
        Guide(onClose = { guideOpen = false })
        return
    }
    if (syncOpen) {
        PhoneSyncScreen(app, onClose = { syncOpen = false })
        return
    }
    BackHandler(onBack = onClose)
    val settings = app.settings
    val snackbarHost = remember { SnackbarHostState() }
    val backup = rememberBackupFiles(app.data.backups) { snackbarHost.showSnackbar(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder: Uri? ->
        if (folder != null) {
            context.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.chooseBackupFolder(folder.toString())
            app.backUpIfDue()
        }
    }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

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
            SyncSetting(app.syncController) { syncOpen = true }
            Setting(stringResource(Res.string.export_tasks), null, backup.export)
            Setting(stringResource(Res.string.import_tasks), null, backup.import)
            Setting(stringResource(Res.string.import_calendar), null, backup.importCalendar)
            Setting(
                label = stringResource(Res.string.setting_toolbar),
                value = stringResource(if (settings.toolbarAbove) Res.string.toolbar_keyboard else Res.string.toolbar_top),
            ) { settings.chooseToolbarAbove(!settings.toolbarAbove) }
            ThemeSetting(settings)
            Setting(
                label = stringResource(Res.string.setting_backup),
                value = settings.backupFolder?.let { folderName(it) } ?: stringResource(Res.string.off),
            ) {
                if (settings.backupFolder != null) settings.chooseBackupFolder(null) else pickFolder.launch(null)
            }
            UpdateRows(app.updates, version) { ready -> installOnPhone(context, ready) }
            val widgets = remember { AppWidgetManager.getInstance(context) }
            if (widgets.isRequestPinAppWidgetSupported) {
                Setting(stringResource(Res.string.setting_widget), null) {
                    widgets.requestPinAppWidget(ComponentName(context, PlannerWidget::class.java), null, null)
                }
            }
            Setting(stringResource(Res.string.guide_title), null) { guideOpen = true }
            VersionLine(version)
        }
    }
}

/** The sync screen on a phone: it asks for the local network first, and pairs by scanning another device's code. */
@Composable
private fun PhoneSyncScreen(app: PlannerApp, onClose: () -> Unit) {
    val context = LocalContext.current
    // Android 17 asks before an app may use the home network; sync cannot reach the other device without it
    var networkAllowed by remember { mutableStateOf(context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) }
    val allowNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { networkAllowed = it }
    LaunchedEffect(Unit) { if (!networkAllowed) allowNetwork.launch(LOCAL_NETWORK) }
    var onCode by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val camera = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { onCode?.invoke(it) } }
    SyncScreen(
        app.syncController,
        scan = { then ->
            onCode = then
            camera.launch(
                ScanOptions()
                    .setPrompt("")
                    .setBeepEnabled(false)
                    .setOrientationLocked(true)
                    .setCaptureActivity(ScanActivity::class.java)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE),
            )
        },
        // a refusal of the permission is the one cause the person can fix, so it alone is named
        failed = stringResource(if (networkAllowed) Res.string.sync_failed_phone else Res.string.sync_blocked),
        onClose = onClose,
    )
}

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
