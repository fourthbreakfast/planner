package dev.fourthbreakfast.planner.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.fourthbreakfast.planner.UiSettings
import dev.fourthbreakfast.planner.core.Backups
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DAY = 24 * 60 * 60 * 1000L
private const val KEEP = 7
private const val PREFIX = "planner-backup-"

/**
 * Writes a backup file into the folder a person chose, at most once a day, and keeps the last week of them.
 * The folder is picked through the phone's own file picker, so the app needs no storage permission.
 */
class AutoBackup(private val context: Context, private val backups: Backups, private val settings: UiSettings) {
    suspend fun runIfDue(now: Long = System.currentTimeMillis()) {
        val folder = settings.backupFolder ?: return
        if (now - settings.lastBackup < DAY) return
        val tree = Uri.parse(folder)
        val text = backups.export()
        try {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val file = DocumentsContract.createDocument(resolver, parent, "application/json", PREFIX + LocalDate.now() + ".json")
                    ?: throw IOException("no file")
                resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("no file")
                prune(tree)
            }
            settings.markBackedUp(now)
        } catch (_: SecurityException) {
            // the folder was taken away: stop trying
            settings.chooseBackupFolder(null)
        } catch (_: IOException) {
            // try again next time
        } catch (_: IllegalArgumentException) {
            settings.chooseBackupFolder(null)
        }
    }

    private fun prune(tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val old = ArrayList<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name.startsWith(PREFIX) && name.endsWith(".json")) old += cursor.getString(0) to name
            }
        }
        old.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

/** A folder address as something short to read: "documents/backups". */
fun folderName(folder: String): String =
    Uri.decode(Uri.parse(folder).lastPathSegment.orEmpty()).substringAfter(':').ifEmpty { "/" }
