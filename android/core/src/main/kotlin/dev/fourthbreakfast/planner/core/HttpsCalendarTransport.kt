package dev.fourthbreakfast.planner.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Private feed URLs are never included in failures. No cookies, ambient credentials, or insecure redirects are used. */
class HttpsCalendarTransport(private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) : CalendarTransport {
    override suspend fun fetch(subscription: CalendarSubscription): CalendarDownload = withContext(Dispatchers.IO) {
        val original = URI(calendarUrl(subscription.url))
        var uri = original
        val deadline = System.nanoTime() + 45_000_000_000L
        repeat(6) { hop ->
            ensureActive()
            if (System.nanoTime() > deadline) throw IOException("calendar request timed out")
            val connection = connect(uri)
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "planner")
                connection.setRequestProperty("Accept", "text/calendar, application/ics;q=0.9, */*;q=0.1")
                connection.setRequestProperty("Accept-Encoding", "gzip")
                if (uri.host.equals(original.host, true) && uri.port == original.port) {
                    subscription.etag?.let { connection.setRequestProperty("If-None-Match", it) }
                    subscription.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
                }
                when (connection.responseCode) {
                    200 -> {
                        if (connection.contentLengthLong > MAX_BYTES) throw CalendarFeedException(CalendarFailure.TOO_LARGE)
                        val encoding = connection.getHeaderField("Content-Encoding").orEmpty()
                        val stream = when {
                            encoding.isBlank() || encoding.equals("identity", true) -> connection.inputStream
                            encoding.equals("gzip", true) -> GZIPInputStream(connection.inputStream)
                            else -> throw CalendarFeedException(CalendarFailure.INVALID_FEED)
                        }
                        val bytes = stream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                ensureActive()
                                if (System.nanoTime() > deadline) throw IOException("calendar request timed out")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_BYTES) throw CalendarFeedException(CalendarFailure.TOO_LARGE)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val text = try {
                            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes)).toString()
                        } catch (_: java.nio.charset.CharacterCodingException) {
                            throw CalendarFeedException(CalendarFailure.INVALID_FEED)
                        }
                        return@withContext CalendarDownload(text, validator(connection, "ETag"), validator(connection, "Last-Modified"))
                    }
                    304 -> return@withContext CalendarDownload(
                        etag = validator(connection, "ETag"),
                        lastModified = validator(connection, "Last-Modified"),
                        notModified = true,
                    )
                    301, 302, 303, 307, 308 -> {
                        if (hop == 5) throw CalendarFeedException(CalendarFailure.SERVER)
                        val location = connection.getHeaderField("Location") ?: throw CalendarFeedException(CalendarFailure.SERVER)
                        uri = try {
                            URI(calendarUrl(uri.resolve(location).toString()))
                        } catch (e: CalendarFeedException) {
                            throw e
                        } catch (_: IllegalArgumentException) {
                            throw CalendarFeedException(CalendarFailure.SERVER)
                        }
                    }
                    401, 403 -> throw CalendarFeedException(CalendarFailure.AUTHENTICATION)
                    else -> throw CalendarFeedException(CalendarFailure.SERVER)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw CalendarFeedException(CalendarFailure.SERVER)
    }

    private fun validator(connection: HttpURLConnection, name: String): String? = connection.getHeaderField(name)
        ?.takeIf { it.length <= 4096 && '\r' !in it && '\n' !in it }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
