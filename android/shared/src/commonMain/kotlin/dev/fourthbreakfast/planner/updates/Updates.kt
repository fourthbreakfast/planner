package dev.fourthbreakfast.planner.updates

import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val LATEST = "https://api.github.com/repos/fourthbreakfast/planner/releases/latest"
private const val PREF_DAILY = "update_daily"
private const val PREF_LAST = "update_last_check"
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** A published version, with where its phone and windows files are. */
data class Release(val version: String, val apk: String?, val msi: String?)

/** Whether [candidate] ("0.9.0" or "v0.9.0") is a later version than [current]. */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(version: String) = version.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** What a release page on GitHub says, read into a [Release]. */
fun parseRelease(json: String): Release? {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
    val assets = root["assets"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.content }
    return Release(tag.removePrefix("v"), assets.firstOrNull { it.endsWith(".apk") }, assets.firstOrNull { it.endsWith(".msi") })
}

/** Where a check for updates stands, for the settings to show. */
sealed interface UpdateState {
    data object Unknown : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val release: Release) : UpdateState

    data object Downloading : UpdateState

    data object Failed : UpdateState
}

/**
 * Looks at the latest release on GitHub: once a day on its own, unless that is switched off, and whenever asked. It is
 * the only thing the app sends out besides sync, and it sends nothing about the person, only the request for the page.
 */
class Updates(private val prefs: Prefs, private val current: String, private val clock: () -> Long = System::currentTimeMillis) {
    private val now = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = now

    var daily: Boolean
        get() = prefs.getBoolean(PREF_DAILY, true)
        set(value) = prefs.putBoolean(PREF_DAILY, value)

    /** Checks if the daily check is on and the last one was a day ago or more. */
    suspend fun checkIfDue() {
        if (daily && clock() - prefs.getLong(PREF_LAST, 0) >= DAY_MILLIS) check()
    }

    suspend fun check() {
        now.value = UpdateState.Checking
        val release = try {
            withContext(Dispatchers.IO) { parseRelease(read(LATEST)) }
        } catch (_: IOException) {
            null
        }
        prefs.putLong(PREF_LAST, clock())
        now.value = when {
            release == null -> UpdateState.Failed
            isNewer(release.version, current) -> UpdateState.Available(release)
            else -> UpdateState.UpToDate
        }
    }

    /** Fetches [url] into [target], for the device to install. Returns false when it could not. */
    suspend fun download(url: String, target: File): Boolean {
        val before = now.value
        now.value = UpdateState.Downloading
        val done = try {
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "planner")
                connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                connection.disconnect()
            }
            true
        } catch (_: IOException) {
            false
        }
        now.value = if (done) before else UpdateState.Failed
        return done
    }

    private fun read(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            // github asks every request to say what it comes from
            connection.setRequestProperty("User-Agent", "planner")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) throw IOException("github said ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}
