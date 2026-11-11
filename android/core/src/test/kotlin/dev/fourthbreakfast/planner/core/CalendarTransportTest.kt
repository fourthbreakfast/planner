package dev.fourthbreakfast.planner.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarTransportTest {
    private class Connection(
        val status: Int = 200,
        val body: ByteArray = "BEGIN:VCALENDAR\r\nEND:VCALENDAR".toByteArray(),
        val headers: Map<String, String> = emptyMap(),
    ) : HttpURLConnection(URL("https://example.com/feed")) {
        var disconnected = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun getHeaderField(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun getContentLengthLong() = headers["Content-Length"]?.toLong() ?: -1L
        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
    }

    private val source = CalendarSubscription("source", "work", "https://example.com/private?token=secret", 1, etag = "version", lastModified = "date")

    @Test
    fun crossOriginRedirectDoesNotForwardConditionalHeadersAndEveryConnectionCloses() = runBlocking {
        val redirect = Connection(302, headers = mapOf("Location" to "https://cdn.example.com/feed"))
        val final = Connection(headers = mapOf("ETag" to "new", "Last-Modified" to "today"))
        val seen = mutableListOf<URI>()
        val transport = HttpsCalendarTransport { uri ->
            seen += uri
            if (seen.size == 1) redirect else final
        }
        val result = transport.fetch(source)
        assertEquals("version", redirect.getRequestProperty("If-None-Match"))
        assertEquals(null, final.getRequestProperty("If-None-Match"))
        assertEquals(null, final.getRequestProperty("If-Modified-Since"))
        assertEquals(null, final.getRequestProperty("Cookie"))
        assertEquals(null, final.getRequestProperty("Authorization"))
        assertEquals("cdn.example.com", seen.last().host)
        assertEquals("new", result.etag)
        assertTrue(redirect.disconnected && final.disconnected)
        assertFalse(redirect.instanceFollowRedirects)
        assertEquals(15_000, final.readTimeout)
        assertEquals(15_000, final.connectTimeout)
    }

    @Test
    fun insecureAndCredentialBearingRedirectsAreRejectedWithoutFollowingThem() = runBlocking {
        for (target in listOf("http://example.com/feed", "https://user:password@example.com/feed")) {
            val redirect = Connection(302, headers = mapOf("Location" to target))
            var requests = 0
            val error = runCatching {
                HttpsCalendarTransport {
                    requests++
                    redirect
                }.fetch(source)
            }.exceptionOrNull() as CalendarFeedException
            assertEquals(CalendarFailure.INVALID_URL, error.reason)
            assertEquals(1, requests)
            assertTrue(redirect.disconnected)
            assertEquals(null, error.message)
        }
    }

    @Test
    fun redirectsAreBoundedAndAuthenticationFailuresHaveNoPrivateUrlInTheirErrors() = runBlocking {
        var requests = 0
        val error = runCatching {
            HttpsCalendarTransport {
                requests++
                Connection(302, headers = mapOf("Location" to "/loop?token=secret"))
            }.fetch(source)
        }.exceptionOrNull() as CalendarFeedException
        assertEquals(6, requests)
        assertEquals(CalendarFailure.SERVER, error.reason)
        val unauthorized = Connection(401)
        val auth = runCatching { HttpsCalendarTransport { unauthorized }.fetch(source) }.exceptionOrNull() as CalendarFeedException
        assertEquals(CalendarFailure.AUTHENTICATION, auth.reason)
        assertFalse(auth.toString().contains("secret"))
        assertTrue(unauthorized.disconnected)
    }

    @Test
    fun gzipUnicodeAndNotModifiedAreHandledWithoutAnExtraBodyRead() = runBlocking {
        val text = "BEGIN:VCALENDAR\r\nX-WR-CALNAME:café → school\r\nEND:VCALENDAR"
        val bytes = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val gzip = Connection(body = bytes, headers = mapOf("Content-Encoding" to "gzip"))
        assertEquals(text, HttpsCalendarTransport { gzip }.fetch(source).text)
        val unchanged = Connection(304)
        assertTrue(HttpsCalendarTransport { unchanged }.fetch(source).notModified)
        assertTrue(gzip.disconnected && unchanged.disconnected)
    }

    @Test
    fun decompressedSizeAndMalformedUtf8AreRejectedAndConnectionsClose() = runBlocking {
        val huge = ByteArray(HttpsCalendarTransport.MAX_BYTES + 1) { 'x'.code.toByte() }
        val compressed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(huge) } }.toByteArray()
        for (connection in listOf(
            Connection(body = huge),
            Connection(body = compressed, headers = mapOf("Content-Encoding" to "gzip")),
            Connection(headers = mapOf("Content-Length" to (HttpsCalendarTransport.MAX_BYTES + 1).toString())),
        )) {
            val error = runCatching { HttpsCalendarTransport { connection }.fetch(source) }.exceptionOrNull() as CalendarFeedException
            assertEquals(CalendarFailure.TOO_LARGE, error.reason)
            assertTrue(connection.disconnected)
        }
        val malformed = Connection(body = byteArrayOf(0xc3.toByte(), 0x28))
        val error = runCatching { HttpsCalendarTransport { malformed }.fetch(source) }.exceptionOrNull() as CalendarFeedException
        assertEquals(CalendarFailure.INVALID_FEED, error.reason)
        assertTrue(malformed.disconnected)
    }

    @Test
    fun malformedRedirectsAndInvalidCacheHeadersCannotEscapeOrBePersisted() = runBlocking {
        val redirect = Connection(302, headers = mapOf("Location" to "https://broken host/feed?secret=hidden"))
        val error = runCatching { HttpsCalendarTransport { redirect }.fetch(source) }.exceptionOrNull() as CalendarFeedException
        assertEquals(CalendarFailure.SERVER, error.reason)
        assertTrue(redirect.disconnected)
        val response = Connection(headers = mapOf("ETag" to "bad\r\nheader", "Last-Modified" to "x".repeat(5000)))
        val result = HttpsCalendarTransport { response }.fetch(source)
        assertEquals(null, result.etag)
        assertEquals(null, result.lastModified)
    }
}
