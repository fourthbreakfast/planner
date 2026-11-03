package dev.fourthbreakfast.planner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.fourthbreakfast.planner.ui.Prefs

private const val TOOLBAR_ABOVE = "toolbar_above"
private const val THEME = "theme"
private const val BACKUP_FOLDER = "backup_folder"
private const val LAST_BACKUP = "last_backup"

/** Saved by position, so new choices only ever go at the end. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** The few things a person can set. Kept on the phone, and read by whichever screen needs them. */
class UiSettings(private val prefs: Prefs) {

    /** Whether the notes' formatting bar sits above the keyboard instead of at the top of the note. */
    var toolbarAbove by mutableStateOf(prefs.getBoolean(TOOLBAR_ABOVE, false))
        private set

    fun chooseToolbarAbove(above: Boolean) {
        toolbarAbove = above
        prefs.putBoolean(TOOLBAR_ABOVE, above)
    }

    var theme by mutableStateOf(ThemeChoice.entries.getOrElse(prefs.getInt(THEME, 0)) { ThemeChoice.SYSTEM })
        private set

    fun chooseTheme(theme: ThemeChoice) {
        this.theme = theme
        prefs.putInt(THEME, theme.ordinal)
    }

    /** The folder, as a document tree address, that a backup is written to once a day, or null for none. */
    var backupFolder by mutableStateOf(prefs.getString(BACKUP_FOLDER))
        private set

    fun chooseBackupFolder(folder: String?) {
        backupFolder = folder
        prefs.putString(BACKUP_FOLDER, folder)
        prefs.putLong(LAST_BACKUP, 0)
    }

    val lastBackup: Long get() = prefs.getLong(LAST_BACKUP, 0)

    fun markBackedUp(at: Long) {
        prefs.putLong(LAST_BACKUP, at)
    }
}
