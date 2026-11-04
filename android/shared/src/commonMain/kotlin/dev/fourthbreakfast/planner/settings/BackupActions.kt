package dev.fourthbreakfast.planner.settings

import dev.fourthbreakfast.planner.core.BackupException
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.resources.Res
import dev.fourthbreakfast.planner.resources.calendar_done
import dev.fourthbreakfast.planner.resources.export_done
import dev.fourthbreakfast.planner.resources.export_failed
import dev.fourthbreakfast.planner.resources.import_done
import dev.fourthbreakfast.planner.resources.import_failed
import dev.fourthbreakfast.planner.resources.import_nothing_new
import java.io.IOException
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** The name a saved backup is offered under. */
const val BACKUP_FILE_NAME = "planner-backup.json"

/**
 * Saves a backup through [write], which puts the text in the file the person chose, and says how it went. A file that
 * cannot be written, or that the system took away again, ends in a message, not a crash.
 */
suspend fun exportBackup(backups: Backups, write: suspend (String) -> Unit): String = try {
    write(backups.export())
    getString(Res.string.export_done)
} catch (_: IOException) {
    getString(Res.string.export_failed)
} catch (_: SecurityException) {
    getString(Res.string.export_failed)
}

/**
 * Brings in a backup, or a calendar file when [calendar], from the text [read] fetches, and says how it went: how many
 * things were added, nothing new, or that the file could not be read.
 */
suspend fun importFile(backups: Backups, calendar: Boolean, read: suspend () -> String): String = try {
    val text = read()
    val added = if (calendar) backups.importCalendar(text) else backups.import(text)
    when {
        added == 0 -> getString(Res.string.import_nothing_new)
        calendar -> getPluralString(Res.plurals.calendar_done, added, added)
        else -> getPluralString(Res.plurals.import_done, added, added)
    }
} catch (_: BackupException) {
    getString(Res.string.import_failed)
} catch (_: IOException) {
    getString(Res.string.import_failed)
} catch (_: SecurityException) {
    getString(Res.string.import_failed)
}
