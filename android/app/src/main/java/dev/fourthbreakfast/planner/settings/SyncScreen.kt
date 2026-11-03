package dev.fourthbreakfast.planner.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.fourthbreakfast.planner.PlannerApp
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.sync.shortId
import dev.fourthbreakfast.planner.ui.QuietButton
import dev.fourthbreakfast.planner.ui.theme.Space
import kotlinx.coroutines.launch

private const val SYNCTHING = "https://syncthing.net"

/**
 * Pairing and the devices this one syncs with. While it is open the app's Syncthing runs and lets in any device that
 * asks, so pairing takes this screen open on both devices: one scans or types the other's code.
 */
@Composable
fun SyncScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val controller = (context.applicationContext as PlannerApp).syncController
    DisposableEffect(controller) {
        controller.openPairing()
        onDispose { controller.closePairing() }
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    var anywhere by remember { mutableStateOf(controller.anywhere) }
    val colors = MaterialTheme.colorScheme

    fun pair(text: String) {
        scope.launch {
            notFound = !controller.pairWith(text)
            if (!notFound) code = ""
        }
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(::pair) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsBar(stringResource(R.string.sync_title), onClose) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!controller.available || state.failed) {
                Line(stringResource(R.string.sync_unavailable), null)
                return@Column
            }
            Line(stringResource(R.string.sync_this_device), if (state.myId.isEmpty()) "…" else shortId(state.myId))
            if (state.myId.isNotEmpty()) {
                QrCode(state.myId, Modifier.padding(horizontal = Space.gutter, vertical = Space.x2).size(176.dp))
            }
            Setting(stringResource(R.string.sync_scan), null) {
                scan.launch(ScanOptions().setPrompt("").setBeepEnabled(false).setOrientationLocked(true).setDesiredBarcodeFormats(ScanOptions.QR_CODE))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.gutter, end = Space.rowPad),
            ) {
                Box(Modifier.weight(1f)) {
                    if (code.isEmpty()) {
                        Text(stringResource(R.string.sync_type_code), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
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
                QuietButton(stringResource(if (notFound) R.string.sync_not_found else R.string.sync_pair), code.isNotBlank()) { pair(code) }
            }
            for (peer in state.peers) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(start = Space.gutter, end = Space.rowPad),
                ) {
                    Text(
                        text = peer.name.ifBlank { shortId(peer.id) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(if (peer.connected) R.string.sync_connected else R.string.sync_waiting),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    QuietButton(stringResource(R.string.sync_remove)) { controller.unpair(peer.id) }
                }
            }
            Setting(
                stringResource(R.string.sync_anywhere),
                stringResource(if (anywhere) R.string.sync_on else R.string.backup_off),
            ) {
                anywhere = !anywhere
                controller.chooseAnywhere(anywhere)
            }
            Text(
                text = stringResource(R.string.sync_credit),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SYNCTHING))) }
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
