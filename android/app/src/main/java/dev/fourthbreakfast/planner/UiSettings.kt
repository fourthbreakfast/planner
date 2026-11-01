package dev.fourthbreakfast.planner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS = "view"
private const val TOOLBAR_ABOVE = "toolbar_above"

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
}
