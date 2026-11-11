package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.io.IOException
import java.util.Locale

/** Match the executable and the complete --home argument, never an unrelated Syncthing with a similar path. */
internal fun isPrivateSyncthing(command: String?, arguments: Array<String>?, home: File): Boolean {
    if (command == null || arguments == null) return false
    if (File(command).name.lowercase(Locale.ROOT) !in setOf("syncthing", "syncthing.exe")) return false
    val option = arguments.firstOrNull { it.startsWith("--home=") }?.substringAfter('=')
        ?: arguments.indexOf("--home").takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }
        ?: return false
    return try {
        File(option).canonicalFile == home.canonicalFile
    } catch (_: IOException) {
        false
    }
}
