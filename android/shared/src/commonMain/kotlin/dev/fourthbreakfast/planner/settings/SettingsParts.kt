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
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.guide
import dev.fourthbreakfast.planner.resources.guide_title
import dev.fourthbreakfast.planner.ui.BackButton
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.SegmentTab
import dev.fourthbreakfast.planner.ui.theme.Space
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
fun Guide(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val entries = stringArrayResource(Res.array.guide)
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
