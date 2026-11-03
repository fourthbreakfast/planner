package dev.fourthbreakfast.planner.desktop

import java.util.concurrent.TimeUnit

private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val VALUE = "planner"

/**
 * Starting with Windows, in the tray: the app's entry in the user's own Run key. Only the installed app can do it,
 * since it needs the path of planner.exe, which jpackage hands over.
 */
object StartWithWindows {
    private val exe: String? = System.getProperty("jpackage.app-path")

    val possible: Boolean get() = exe != null

    val on: Boolean get() = run("reg", "query", RUN_KEY, "/v", VALUE)

    fun set(on: Boolean) {
        val path = exe ?: return
        if (on) {
            run("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$path\" --tray", "/f")
        } else {
            run("reg", "delete", RUN_KEY, "/v", VALUE, "/f")
        }
    }

    private fun run(vararg command: String): Boolean = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)
}
