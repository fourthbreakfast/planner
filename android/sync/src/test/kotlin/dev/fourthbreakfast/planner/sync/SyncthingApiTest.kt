package dev.fourthbreakfast.planner.sync

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The REST interface against a stand-in that answers what it is told to, so odd answers can be tried. */
class SyncthingApiTest {
    private var answer = "{}"
    private var lastKey: String? = null
    private var lastBody = ""
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            lastKey = exchange.requestHeaders.getFirst("X-API-Key")
            lastBody = exchange.requestBody.readBytes().decodeToString()
            val bytes = answer.toByteArray()
            val code = if (exchange.requestURI.path == "/missing") 404 else 200
            exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
    private val api = SyncthingApi(server.address.port, "secret")

    @After
    fun stop() = server.stop(0)

    @Test
    fun everyCallCarriesTheKey() = runBlocking {
        api.get("/rest/system/status")
        assertEquals("secret", lastKey)
    }

    @Test
    fun aBrokenAnswerIsAFailureToTalkToIt() = runBlocking {
        answer = "{\"half\": "
        val failure = runCatching { api.get("/rest/system/status") }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
    }

    @Test
    fun aPatchSendsBackTheWholeObjectWithTheFieldsChanged() = runBlocking {
        answer = """{"name": "old", "address": "a"}"""
        api.patch("/rest/config/devices/x", JsonObject(mapOf("name" to JsonPrimitive("new"))))
        assertEquals("""{"name":"new","address":"a"}""", lastBody)
    }

    @Test
    fun aPatchOfSomethingThatIsNotAnObjectFails() = runBlocking {
        answer = "[1, 2]"
        assertTrue(runCatching { api.patch("/rest/config/gui", JsonObject(emptyMap())) }.exceptionOrNull() is IOException)
    }

    @Test
    fun notFoundIsToldApart() = runBlocking {
        assertTrue(runCatching { api.get("/missing") }.exceptionOrNull() is SyncthingApi.NotFound)
    }

    @Test
    fun nothingAnswersMeansNotAlive() = runBlocking {
        server.stop(0)
        assertEquals(false, api.alive())
    }
}
