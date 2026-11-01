package dev.fourthbreakfast.planner.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.tasks.BackButton
import dev.fourthbreakfast.planner.tasks.DiscreetMessage
import dev.fourthbreakfast.planner.tasks.SegmentTab
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.tasks.rememberBackupFiles
import dev.fourthbreakfast.planner.ui.theme.Space

/** What can be set, the backup, and a list of every gesture. Reached by holding any of the three tabs. */
@Composable
fun SettingsScreen(viewModel: TasksViewModel, onClose: () -> Unit) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    if (guideOpen) {
        Guide(onClose = { guideOpen = false })
        return
    }
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val settings = (context.applicationContext as PlannerApp).settings
    val snackbarHost = remember { SnackbarHostState() }
    val backup = rememberBackupFiles(viewModel) { snackbarHost.showSnackbar(it) }
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
            Setting(stringResource(R.string.export_tasks), null, backup.export)
            Setting(stringResource(R.string.import_tasks), null, backup.import)
            Setting(
                label = stringResource(R.string.setting_toolbar),
                value = stringResource(if (settings.toolbarAbove) R.string.toolbar_keyboard else R.string.toolbar_top),
            ) { settings.chooseToolbarAbove(!settings.toolbarAbove) }
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

@Composable
private fun SettingsBar(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Space.gutter, end = Space.rowPad),
    ) {
        SegmentTab(title, Modifier.weight(1f))
        BackButton(onClose)
    }
}

/** One line of the settings: what it is, and the choice it holds now when it is a choice. */
@Composable
private fun Setting(label: String, value: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (value != null) Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** Every gesture, one to a line: what to do, then what it does. */
@Composable
private fun Guide(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val entries = stringArrayResource(R.array.guide)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsBar(stringResource(R.string.guide_title), onClose) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                val (gesture, result) = entry.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.x3)) {
                    Text(text = gesture, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(text = result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
