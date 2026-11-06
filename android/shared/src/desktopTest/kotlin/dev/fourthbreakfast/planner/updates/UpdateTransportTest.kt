package dev.fourthbreakfast.planner.updates

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTransportTest {
    @Test
    fun redirectsResolveRelativeLocationsAndCloseEveryConnection() {
        val connections = mutableListOf<FixtureConnection>()
        val transport = HttpsUpdateTransport { uri ->
            FixtureConnection(uri, if (connections.isEmpty()) 302 else 200, "/installer.apk").also { connections += it }
        }
        transport.open("https://example.test/releases/latest", json = true).use { input ->
            assertEquals("fixture", input.readBytes().toString(Charsets.UTF_8))
            assertEquals("https://example.test/installer.apk", connections[1].url.toString())
            assertTrue(connections[0].disconnected)
            assertFalse(connections[1].disconnected)
            assertFalse(connections[0].instanceFollowRedirects)
            assertEquals("planner", connections[0].getRequestProperty("User-Agent"))
            assertEquals("application/vnd.github+json", connections[0].getRequestProperty("Accept"))
        }
        assertTrue(connections.all { it.disconnected })
    }

    @Test
    fun insecureOrMalformedInitialUrlsNeverOpenAConnection() {
        val transport = HttpsUpdateTransport { error("must not connect") }
        listOf("http://example.test/a.apk", "file:///a.apk", "https:///a.apk", "https://user:secret@example.test/a.apk", "bad URL")
            .forEach { url -> assertThrows(IOException::class.java) { transport.open(url, false) } }
    }

    @Test
    fun redirectsCannotDowngradeToHttpOrLocalFiles() {
        for (location in listOf("http://example.test/a.apk", "file:///a.apk", "https://user:secret@example.test/a.apk", "bad URL")) {
            val connections = mutableListOf<FixtureConnection>()
            val transport = HttpsUpdateTransport { uri ->
                FixtureConnection(uri, 302, location).also { connections += it }
            }
            assertThrows(IOException::class.java) { transport.open("https://example.test/a.apk", false) }
            assertEquals(1, connections.size)
            assertTrue(connections.single().disconnected)
        }
    }

    @Test
    fun redirectLoopsAndServerErrorsCloseConnectionsAndFail() {
        for (status in listOf(302, 404, 500)) {
            val connections = mutableListOf<FixtureConnection>()
            val transport = HttpsUpdateTransport { uri ->
                FixtureConnection(uri, status, "/loop").also { connections += it }
            }
            assertThrows(IOException::class.java) { transport.open("https://example.test/a.apk", false) }
            assertEquals(if (status == 302) 6 else 1, connections.size)
            assertTrue(connections.all { it.disconnected })
        }
    }

    @Test
    fun missingRedirectLocationsFailAndDisconnect() {
        val connection = FixtureConnection(URI("https://example.test/a.apk"), 302, null)
        val transport = HttpsUpdateTransport { connection }
        assertThrows(IOException::class.java) { transport.open(connection.url.toString(), false) }
        assertTrue(connection.disconnected)
    }
}

private class FixtureConnection(uri: URI, private val status: Int, private val location: String?) : HttpURLConnection(uri.toURL()) {
    var disconnected = false

    override fun connect() = Unit

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy() = false

    override fun getResponseCode() = status

    override fun getHeaderField(name: String) = if (name == "Location") location else null

    override fun getInputStream(): InputStream = "fixture".byteInputStream()
}
