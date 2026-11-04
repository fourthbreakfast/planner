package dev.fourthbreakfast.planner.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.backup_off
import dev.fourthbreakfast.planner.resources.sync_anywhere
import dev.fourthbreakfast.planner.resources.sync_asks
import dev.fourthbreakfast.planner.resources.sync_connected
import dev.fourthbreakfast.planner.resources.sync_credit
import dev.fourthbreakfast.planner.resources.sync_let_in
import dev.fourthbreakfast.planner.resources.sync_name
import dev.fourthbreakfast.planner.resources.sync_not_found
import dev.fourthbreakfast.planner.resources.sync_now
import dev.fourthbreakfast.planner.resources.sync_on
import dev.fourthbreakfast.planner.resources.sync_pair
import dev.fourthbreakfast.planner.resources.sync_refuse
import dev.fourthbreakfast.planner.resources.sync_remove
import dev.fourthbreakfast.planner.resources.sync_scan
import dev.fourthbreakfast.planner.resources.sync_syncing
import dev.fourthbreakfast.planner.resources.sync_this_device
import dev.fourthbreakfast.planner.resources.sync_title
import dev.fourthbreakfast.planner.resources.sync_type_code
import dev.fourthbreakfast.planner.resources.sync_unavailable
import dev.fourthbreakfast.planner.resources.sync_waiting
import dev.fourthbreakfast.planner.sync.SyncControls
import dev.fourthbreakfast.planner.sync.pairText
import dev.fourthbreakfast.planner.sync.shortId
import dev.fourthbreakfast.planner.ui.BackHandler
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val SYNCTHING = "https://syncthing.net"

/**
 * Pairing and the devices this one syncs with. While it is open the app's Syncthing runs and shows any device asking to
 * sync, to be let in or turned down, so pairing takes this screen open on both devices: one scans or types the other's
 * code, and the other says yes. [failed] says why sync could not start, in the device's own terms. A device without a
 * camera to [scan] with only offers typing.
 */
@Composable
fun SyncScreen(controller: SyncControls, scan: ((onCode: (String) -> Unit) -> Unit)?, failed: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val links = LocalUriHandler.current
    DisposableEffect(controller) {
        controller.openPairing()
        onDispose { controller.closePairing() }
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var anywhere by remember { mutableStateOf(controller.anywhere) }
    val colors = MaterialTheme.colorScheme

    fun pair(text: String) {
        scope.launch {
            notFound = !controller.pairWith(text)
            if (!notFound) code = ""
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsBar(stringResource(Res.string.sync_title), onClose) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!controller.available || state.failed) {
                Line(if (controller.available) failed else stringResource(Res.string.sync_unavailable), null)
                return@Column
            }
            Line(stringResource(Res.string.sync_this_device), if (state.myId.isEmpty()) "…" else shortId(state.myId))
            NameLine(stringResource(Res.string.sync_name), controller.deviceName, controller::renameThisDevice)
            if (state.myId.isNotEmpty()) {
                QrCode(pairText(state.myId, state.addresses), Modifier.padding(horizontal = Space.gutter, vertical = Space.x2).size(176.dp))
            }
            if (scan != null) Setting(stringResource(Res.string.sync_scan), null) { scan(::pair) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.gutter, end = Space.rowPad),
            ) {
                Box(Modifier.weight(1f)) {
                    if (code.isEmpty()) {
                        Text(stringResource(Res.string.sync_type_code), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = code,
                        onValueChange = {
                            code = it.uppercase()
                            notFound = false
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onBackground),
                        cursorBrush = SolidColor(colors.onBackground),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) pair(code) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                QuietButton(stringResource(if (notFound) Res.string.sync_not_found else Res.string.sync_pair), code.isNotBlank()) { pair(code) }
            }
            for (asking in state.asking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    Text(
                        text = stringResource(Res.string.sync_asks, asking.name.ifBlank { shortId(asking.id) }),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton(stringResource(Res.string.sync_let_in)) { controller.letIn(asking.id) }
                    QuietButton(stringResource(Res.string.sync_refuse)) { controller.refuse(asking.id) }
                }
            }
            for (peer in state.peers) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    NameField(peer.name.ifBlank { shortId(peer.id) }, Modifier.weight(1f)) { controller.renamePeer(peer.id, it) }
                    Text(
                        text = stringResource(if (peer.connected) Res.string.sync_connected else Res.string.sync_waiting),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    QuietButton(stringResource(Res.string.sync_remove)) { controller.unpair(peer.id) }
                }
            }
            if (state.peers.isNotEmpty()) {
                Setting(stringResource(Res.string.sync_now), if (syncing) stringResource(Res.string.sync_syncing) else null) {
                    if (!syncing) {
                        syncing = true
                        scope.launch {
                            controller.syncNow()
                            syncing = false
                        }
                    }
                }
            }
            Setting(
                stringResource(Res.string.sync_anywhere),
                stringResource(if (anywhere) Res.string.sync_on else Res.string.backup_off),
            ) {
                anywhere = !anywhere
                controller.chooseAnywhere(anywhere)
            }
            Text(
                text = stringResource(Res.string.sync_credit),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable { links.openUri(SYNCTHING) }
                    .padding(start = Space.gutter, end = Space.gutter, top = Space.x6, bottom = Space.x2),
            )
        }
    }
}

/** A line that only tells something. */
@Composable
private fun Line(label: String, value: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(horizontal = Space.gutter),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** This device's id as a code the other device's camera reads. Always black on white, the only way round every camera reads. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Canvas(modifier.background(Color.White).padding(Space.x2)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** A line that names this device: the name is written in place, kept when the keyboard is done or the line is left. */
@Composable
private fun NameLine(label: String, name: String, onRename: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(horizontal = Space.gutter),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        NameField(name, Modifier.weight(1f).padding(start = Space.x4), endAligned = true, onRename = onRename)
    }
}

/** A name that can be changed where it stands. */
@Composable
private fun NameField(name: String, modifier: Modifier = Modifier, endAligned: Boolean = false, onRename: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember(name) { mutableStateOf(name) }
    BasicTextField(
        value = text,
        onValueChange = { text = it.replace("\n", "") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.onBackground,
            textAlign = if (endAligned) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        ),
        cursorBrush = SolidColor(colors.onBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onRename(text) }),
        modifier = modifier.onFocusChanged { if (!it.isFocused && text != name) onRename(text) },
    )
}
