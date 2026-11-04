package dev.fourthbreakfast.planner.tasks

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.fourthbreakfast.planner.R
import dev.fourthbreakfast.planner.core.BackupException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupFiles(val export: () -> Unit, val import: () -> Unit, val importCalendar: () -> Unit)

private const val FILE_NAME = "planner-backup.json"

/** Saves and opens backups through the phone's own file picker, so the app needs no storage permission. */
@Composable
fun rememberBackupFiles(viewModel: TasksViewModel, say: suspend (String) -> Unit): BackupFiles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = context.resources

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val text = viewModel.exportBackup()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                            ?: throw IOException("no file")
                    }
                    resources.getString(R.string.export_done)
                } catch (_: IOException) {
                    resources.getString(R.string.export_failed)
                } catch (_: SecurityException) {
                    resources.getString(R.string.export_failed)
                }
                say(message)
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: throw IOException("no file")
                    }
                    val added = viewModel.importBackup(text)
                    if (added == 0) {
                        resources.getString(R.string.import_nothing_new)
                    } else {
                        resources.getQuantityString(R.plurals.import_done, added, added)
                    }
                } catch (_: BackupException) {
                    resources.getString(R.string.import_failed)
                } catch (_: IOException) {
                    resources.getString(R.string.import_failed)
                } catch (_: SecurityException) {
                    resources.getString(R.string.import_failed)
                }
                say(message)
            }
        }
    }
    val calendarOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: throw IOException("no file")
                    }
                    val added = viewModel.importCalendar(text)
                    if (added == 0) {
                        resources.getString(R.string.import_nothing_new)
                    } else {
                        resources.getQuantityString(R.plurals.calendar_done, added, added)
                    }
                } catch (_: BackupException) {
                    resources.getString(R.string.import_failed)
                } catch (_: IOException) {
                    resources.getString(R.string.import_failed)
                } catch (_: SecurityException) {
                    resources.getString(R.string.import_failed)
                }
                say(message)
            }
        }
    }
    return remember(saver, opener, calendarOpener) {
        BackupFiles(
            export = { saver.launch(FILE_NAME) },
            import = { opener.launch(arrayOf("*/*")) },
            importCalendar = { calendarOpener.launch(arrayOf("text/calendar", "application/octet-stream", "text/plain")) },
        )
    }
}
