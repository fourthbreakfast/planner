package dev.fourthbreakfast.planner.desktop

import dev.fourthbreakfast.planner.agenda.timeText
import dev.fourthbreakfast.planner.core.Event
import dev.fourthbreakfast.planner.core.reminderAt
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Reminders on a computer: while the app runs, each event's reminder shows as a Windows notification at its time.
 * Nothing rings while the app is closed; the phone keeps doing that.
 */
class DesktopReminders(private val events: Flow<List<Event>>, private val scope: CoroutineScope, private val is24Hour: Boolean, icon: Image) {
    private val tray: TrayIcon? = if (SystemTray.isSupported()) {
        TrayIcon(icon, "planner").also {
            it.isImageAutoSize = true
            runCatching { SystemTray.getSystemTray().add(it) }
        }
    } else {
        null
    }

    fun begin() {
        scope.launch {
            val shown = HashSet<String>()
            events.collectLatest { all ->
                while (true) {
                    val now = System.currentTimeMillis()
                    val due = all.filter { !it.inBin }
                        .mapNotNull { event -> reminderAt(event)?.let { "${event.id}@$it" to event }?.takeIf { (key, _) -> key !in shown } }
                        .filter { (key, _) -> key.substringAfter('@').toLong() in (now - 60_000)..now }
                    for ((key, event) in due) {
                        shown += key
                        val time = event.minute?.let { timeText(it, is24Hour) }.orEmpty()
                        val text = listOf(time, event.location).filter { it.isNotBlank() }.joinToString(", ")
                        tray?.displayMessage(event.title, text, TrayIcon.MessageType.NONE)
                    }
                    delay(15_000)
                }
            }
        }
    }

    fun end() {
        tray?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
    }
}
