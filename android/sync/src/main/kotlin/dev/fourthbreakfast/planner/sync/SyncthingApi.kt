package dev.fourthbreakfast.planner.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Syncthing's REST interface on this device only, opened with the key it was started with. */
class SyncthingApi(private val port: Int, private val key: String) {
    suspend fun get(path: String): JsonElement = call("GET", path, null)

    suspend fun post(path: String, body: JsonElement? = null): JsonElement = call("POST", path, body)

    suspend fun put(path: String, body: JsonElement): JsonElement = call("PUT", path, body)

    /** Changes some fields of a config object. Java's own http client cannot send PATCH, so the whole object goes back. */
    suspend fun patch(path: String, fields: JsonObject): JsonElement {
        val current = get(path) as? JsonObject ?: throw IOException("PATCH $path: not an object")
        return put(path, JsonObject(current + fields))
    }

    suspend fun delete(path: String): JsonElement = call("DELETE", path, null)

    /** Whether it answers at all. */
    suspend fun alive(): Boolean = try {
        call("GET", "/rest/system/ping", null, quick = true)
        true
    } catch (_: IOException) {
        false
    }

    private suspend fun call(method: String, path: String, body: JsonElement?, quick: Boolean = false): JsonElement = withContext(Dispatchers.IO) {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            // a ping that hangs means something stands between the app and its Syncthing, such as a firewall
            connection.connectTimeout = if (quick) 500 else 2_000
            connection.readTimeout = if (quick) 1_000 else 10_000
            connection.setRequestProperty("X-API-Key", key)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NotFound(path)
            if (code !in 200..299) throw IOException("$method $path: $code")
            val text = connection.inputStream.use { it.readBytes().decodeToString() }
            if (text.isBlank()) {
                JsonNull
            } else {
                try {
                    Json.parseToJsonElement(text)
                } catch (e: SerializationException) {
                    throw IOException("$method $path: not json", e)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    class NotFound(path: String) : IOException("not found: $path")
}

internal fun encoded(part: String): String = URLEncoder.encode(part, "UTF-8")
