package dev.fourthbreakfast.planner.settings

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fourthbreakfast.planner.ThemeChoice
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.app_name
import dev.fourthbreakfast.planner.resources.guide
import dev.fourthbreakfast.planner.resources.guide_title
import dev.fourthbreakfast.planner.resources.off
import dev.fourthbreakfast.planner.resources.on
import dev.fourthbreakfast.planner.resources.setting_theme
import dev.fourthbreakfast.planner.resources.sync_devices
import dev.fourthbreakfast.planner.resources.sync_title
import dev.fourthbreakfast.planner.resources.theme_dark
import dev.fourthbreakfast.planner.resources.theme_light
import dev.fourthbreakfast.planner.resources.theme_system
import dev.fourthbreakfast.planner.sync.SyncControls
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.SegmentTab
import dev.fourthbreakfast.planner.ui.theme.Space
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The parts every settings screen is made of, on a phone or a computer. */

@Composable
fun SettingsBar(title: String, onClose: () -> Unit) {
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
fun Setting(label: String, value: String?, onClick: () -> Unit) {
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
fun Guide(list: StringArrayResource = Res.array.guide, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val entries = stringArrayResource(list)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsBar(stringResource(Res.string.guide_title), onClose) },
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

/** The line that opens sync, with how many devices are paired. Left out where sync does not run. */
@Composable
fun SyncSetting(sync: SyncControls, onOpen: () -> Unit) {
    if (!sync.available) return
    val peers = sync.state.collectAsStateWithLifecycle().value.peers.size
    val count = if (peers == 0) null else pluralStringResource(Res.plurals.sync_devices, peers, peers)
    Setting(stringResource(Res.string.sync_title), count, onOpen)
}

/** The theme, which goes round system, light and dark at each touch. */
@Composable
fun ThemeSetting(settings: UiSettings) {
    val name = when (settings.theme) {
        ThemeChoice.LIGHT -> Res.string.theme_light
        ThemeChoice.DARK -> Res.string.theme_dark
        ThemeChoice.SYSTEM -> Res.string.theme_system
    }
    Setting(stringResource(Res.string.setting_theme), stringResource(name)) {
        settings.chooseTheme(ThemeChoice.entries[(settings.theme.ordinal + 1) % ThemeChoice.entries.size])
    }
}

/** The app's name and version, quietly, at the foot of the settings. */
@Composable
fun VersionLine(version: String) {
    Text(
        text = stringResource(Res.string.app_name) + " " + version,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.gutter, top = Space.x6),
    )
}

/** How a setting that is switched on or off reads. */
@Composable
fun onOff(on: Boolean): String = stringResource(if (on) Res.string.on else Res.string.off)
