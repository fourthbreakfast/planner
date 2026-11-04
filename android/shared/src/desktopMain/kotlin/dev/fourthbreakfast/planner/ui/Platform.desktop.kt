package dev.fourthbreakfast.planner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.prefs.Preferences

/** The user's own preferences on this computer, under one node. */
class DesktopPrefs(node: String) : Prefs {
    private val prefs = Preferences.userRoot().node(node)

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) = prefs.putBoolean(key, value)

    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) = prefs.putInt(key, value)

    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)

    override fun putLong(key: String, value: Long) = prefs.putLong(key, value)

    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putString(key: String, value: String?) = if (value == null) prefs.remove(key) else prefs.put(key, value)
}

/**
 * The back steps of what is open, newest last. The window hands escape to the top one, so escape closes whatever
 * would close on a phone's back gesture.
 */
class BackStack {
    private val handlers = ArrayList<() -> Boolean>()

    fun add(handler: () -> Boolean) {
        handlers += handler
    }

    fun remove(handler: () -> Boolean) {
        handlers -= handler
    }

    /** Runs the newest handler that is on, and returns whether there was one. */
    fun back(): Boolean = handlers.asReversed().any { it() }
}

val LocalBackStack = compositionLocalOf { BackStack() }

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val stack = LocalBackStack.current
    val current = rememberUpdatedState(onBack)
    val on = rememberUpdatedState(enabled)
    DisposableEffect(stack) {
        val handler = {
            if (on.value) current.value()
            on.value
        }
        stack.add(handler)
        onDispose { stack.remove(handler) }
    }
}

/** Whether this computer writes times as 19:30 rather than 7:30 pm, from its language and region. */
fun is24HourClock(): Boolean {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern().orEmpty()
    return 'a' !in pattern
}

@Composable
actual fun rememberIs24Hour(): Boolean = remember { is24HourClock() }

@Composable
actual fun rememberAnimationsOn(): Boolean = true

@Composable
actual fun rememberAskToNotify(): () -> Unit = {}

@Composable
actual fun rememberKeyboardShown(): Boolean = false
