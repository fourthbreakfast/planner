package dev.fourthbreakfast.planner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.fourthbreakfast.planner.Requests
import dev.fourthbreakfast.planner.UiSettings

/** A few saved values: shared preferences on a phone, the user's preferences on a computer. */
interface Prefs {
    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, default: Int): Int

    fun putInt(key: String, value: Int)

    fun getLong(key: String, default: Long): Long

    fun putLong(key: String, value: Long)

    fun getString(key: String): String?

    fun putString(key: String, value: String?)
}

/** What every screen may need from the app around it, whichever device it runs on. */
class PlannerServices(val requests: Requests, val settings: UiSettings, val prefs: Prefs)

val LocalServices = staticCompositionLocalOf<PlannerServices> { error("no planner services provided") }

/** Going back: the phone's back gesture, or escape on a computer. */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Whether times read as 19:30 rather than 7:30 pm, following the device. */
@Composable
expect fun rememberIs24Hour(): Boolean

/** Whether the device wants animations at all. */
@Composable
expect fun rememberAnimationsOn(): Boolean

/** Asks to show notifications, where the device needs asking, before the first reminder is set. */
@Composable
expect fun rememberAskToNotify(): () -> Unit

/** Whether an on-screen keyboard is showing. A computer has none, so there it is always false. */
@Composable
expect fun rememberKeyboardShown(): Boolean
