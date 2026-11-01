package dev.fourthbreakfast.planner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS = "view"
private const val TOOLBAR_ABOVE = "toolbar_above"
private const val THEME = "theme"
private const val BACKUP_FOLDER = "backup_folder"
private const val LAST_BACKUP = "last_backup"

/** Saved by position, so new choices only ever go at the end. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** The few things a person can set. Kept on the phone, and read by whichever screen needs them. */
class UiSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the notes' formatting bar sits above the keyboard instead of at the top of the note. */
    var toolbarAbove by mutableStateOf(prefs.getBoolean(TOOLBAR_ABOVE, false))
        private set

    fun chooseToolbarAbove(above: Boolean) {
        toolbarAbove = above
        prefs.edit().putBoolean(TOOLBAR_ABOVE, above).apply()
    }

    var theme by mutableStateOf(ThemeChoice.entries.getOrElse(prefs.getInt(THEME, 0)) { ThemeChoice.SYSTEM })
        private set

    fun chooseTheme(theme: ThemeChoice) {
        this.theme = theme
        prefs.edit().putInt(THEME, theme.ordinal).apply()
    }

    /** The folder, as a document tree address, that a backup is written to once a day, or null for none. */
    var backupFolder by mutableStateOf(prefs.getString(BACKUP_FOLDER, null))
        private set

    fun chooseBackupFolder(folder: String?) {
        backupFolder = folder
        prefs.edit().putString(BACKUP_FOLDER, folder).putLong(LAST_BACKUP, 0).apply()
    }

    val lastBackup: Long get() = prefs.getLong(LAST_BACKUP, 0)

    fun markBackedUp(at: Long) {
        prefs.edit().putLong(LAST_BACKUP, at).apply()
    }
}
