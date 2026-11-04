package dev.fourthbreakfast.planner.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.settings.BACKUP_FILE_NAME
import dev.fourthbreakfast.planner.settings.exportBackup
import dev.fourthbreakfast.planner.settings.importFile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The three things the settings do with files: save a backup, read one back, and read a calendar. */
class BackupFiles(val export: () -> Unit, val import: () -> Unit, val importCalendar: () -> Unit)

/** Saves and opens backups through the phone's own file picker, so the app needs no storage permission. */
@Composable
fun rememberBackupFiles(backups: Backups, say: suspend (String) -> Unit): BackupFiles {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()

    fun read(uri: Uri): suspend () -> String = {
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IOException("no file")
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                say(
                    exportBackup(backups) { text ->
                        withContext(Dispatchers.IO) {
                            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("no file")
                        }
                    },
                )
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { say(importFile(backups, calendar = false, read(uri))) }
    }
    val calendarOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { say(importFile(backups, calendar = true, read(uri))) }
    }
    return remember(saver, opener, calendarOpener) {
        BackupFiles(
            export = { saver.launch(BACKUP_FILE_NAME) },
            import = { opener.launch(arrayOf("*/*")) },
            importCalendar = { calendarOpener.launch(arrayOf("text/calendar", "application/octet-stream", "text/plain")) },
        )
    }
}
