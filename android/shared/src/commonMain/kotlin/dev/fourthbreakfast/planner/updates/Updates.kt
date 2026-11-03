package dev.fourthbreakfast.planner.updates

import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val LATEST = "https://api.github.com/repos/fourthbreakfast/planner/releases/latest"
private const val PREF_DAILY = "update_daily"
private const val PREF_LAST = "update_last_check"
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** One file of a release: where it is, and the checksum github gives for it. */
data class Asset(val url: String, val sha256: String?)

/** A published version, with its phone and windows files. */
data class Release(val version: String, val apk: Asset?, val msi: Asset?)

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
    val assets = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }

    fun asset(ending: String): Asset? {
        val found = assets.firstOrNull { it.text("name").endsWith(ending) } ?: return null
        val digest = found.text("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
        return Asset(found.text("browser_download_url"), digest)
    }
    return Release(tag.removePrefix("v"), asset(".apk"), asset(".msi"))
}

private fun JsonObject.text(name: String): String = this[name]?.jsonPrimitive?.content.orEmpty()

/** Where updating stands, for the settings and the status bar to show. */
sealed interface UpdateState {
    data object Unknown : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Downloading(val release: Release) : UpdateState

    /** The new version is downloaded, checked and waiting to be installed. */
    data class Ready(val release: Release, val file: File) : UpdateState

    data object Failed : UpdateState
}

/**
 * Keeps the app up to date from its GitHub releases: once a day on its own, unless that is switched off, and whenever
 * asked. A newer version is downloaded in the background into [folder], the app's own, and checked against the
 * checksum GitHub publishes; only then is it ready to install. [pick] chooses this device's file from a release. It is
 * the only thing the app sends out besides sync, and it sends nothing about the person, only requests for the files.
 */
class Updates(
    private val prefs: Prefs,
    private val current: String,
    private val folder: File,
    private val pick: (Release) -> Asset?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
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
        if (now.value is UpdateState.Downloading || now.value is UpdateState.Ready) return
        now.value = UpdateState.Checking
        val release = try {
            withContext(Dispatchers.IO) { parseRelease(String(get(LATEST, json = true))) }
        } catch (_: IOException) {
            null
        }
        prefs.putLong(PREF_LAST, clock())
        val asset = release?.let(pick)
        now.value = when {
            release == null -> UpdateState.Failed
            !isNewer(release.version, current) || asset == null -> UpdateState.UpToDate
            else -> download(release, asset)
        }
    }

    private suspend fun download(release: Release, asset: Asset): UpdateState {
        now.value = UpdateState.Downloading(release)
        return try {
            withContext(Dispatchers.IO) {
                folder.mkdirs()
                // whatever an earlier version left here goes first
                folder.listFiles().orEmpty().forEach { it.delete() }
                val file = File(folder, asset.url.substringAfterLast('/'))
                val bytes = get(asset.url, json = false)
                // a file that is not exactly the one published is never installed
                if (asset.sha256 != null && sha256(bytes) != asset.sha256.lowercase()) throw IOException("checksum differs")
                file.writeBytes(bytes)
                UpdateState.Ready(release, file)
            }
        } catch (_: IOException) {
            UpdateState.Failed
        }
    }

    private fun get(url: String, json: Boolean): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            // github asks every request to say what it comes from
            connection.setRequestProperty("User-Agent", "planner")
            if (json) connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) throw IOException("github said ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
