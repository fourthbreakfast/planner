package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Platform decisions stay outside the shared planner rules. Linux packages currently target x86-64 Debian/Ubuntu. */
internal object DesktopPlatform {
    val isWindows: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val isLinux: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

    fun dataFolder(): File = desktopDataFolder(System.getProperty("os.name").orEmpty(), System.getenv(), File(System.getProperty("user.home")))
        .apply { mkdirs() }

    fun syncthingBinary(): File? {
        val resources = System.getProperty("compose.application.resources.dir") ?: return null
        val name = desktopSyncthingName(System.getProperty("os.name").orEmpty()) ?: return null
        return File(resources, name).takeIf { it.isFile }
    }

    fun deviceName(): String {
        val name = System.getenv(if (isWindows) "COMPUTERNAME" else "HOSTNAME")?.takeIf { it.isNotBlank() }
        return (name ?: if (isLinux) "linux" else "computer").lowercase(Locale.ROOT).take(40)
    }

    fun installerCommand(file: File): List<String> = desktopInstallerCommand(file, System.getProperty("os.name").orEmpty())

    fun notify(title: String, text: String) {
        if (!isLinux) return
        try {
            val process = ProcessBuilder("notify-send", "--app-name=planner", "--icon=planner", "--", title, text)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroy()
        } catch (_: IOException) {
            // Portable installs may omit libnotify; the app and its stored reminders still work.
        } catch (_: SecurityException) {
            // A restricted JVM may forbid launching a notification helper.
        }
    }
}

internal fun desktopDataFolder(osName: String, environment: Map<String, String>, home: File): File {
    environment["PLANNER_DATA"]?.takeIf { it.isNotBlank() }?.let { return File(it) }
    if (osName.startsWith("Windows", ignoreCase = true)) {
        return File(environment["APPDATA"]?.let(::File) ?: File(home, ".config"), "planner")
    }
    val dataHome = environment["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isAbsolute }
        ?: File(home, ".local/share")
    val destination = File(dataHome, "planner")
    val former = File(home, ".config/planner")
    // Earlier source builds used .config. Keep using that database rather than appearing to lose it after upgrading.
    return if (!File(destination, "planner.db").exists() && File(former, "planner.db").isFile) former else destination
}

internal fun desktopSyncthingName(osName: String): String? = when {
    osName.startsWith("Windows", ignoreCase = true) -> "syncthing.exe"
    osName.startsWith("Linux", ignoreCase = true) -> "syncthing"
    else -> null
}

/** Linux hands a verified package to the user's software installer; it never invokes sudo or accepts an admin prompt. */
internal fun desktopInstallerCommand(file: File, osName: String): List<String> = when {
    osName.startsWith("Windows", ignoreCase = true) && file.extension.equals("exe", ignoreCase = true) ->
        listOf(file.absolutePath, "/SILENT", "/NORESTART", "/SUPPRESSMSGBOXES")
    osName.startsWith("Linux", ignoreCase = true) && file.extension.equals("deb", ignoreCase = true) -> listOf("xdg-open", file.absolutePath)
    else -> throw IOException("No supported installer for this platform")
}
