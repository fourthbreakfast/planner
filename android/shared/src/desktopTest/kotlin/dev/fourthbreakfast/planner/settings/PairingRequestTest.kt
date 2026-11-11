package dev.fourthbreakfast.planner.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingRequestTest {
    private val fullId = "ABCDEFG" + "A".repeat(45)

    @Test
    fun aSubmittedLocalCodeCanRetryOnceWithTheDiscoveredFullId() {
        val request = PairingRequest(" abc-defg ")
        assertNull(request.discoveredRetry(emptyList()))
        assertEquals(fullId, request.discoveredRetry(listOf(fullId, fullId)))
        assertNull(request.discoveredRetry(listOf(fullId)))
    }

    @Test
    fun ambiguousShortCodesNeverChooseADeviceAutomatically() {
        val request = PairingRequest("ABCDEFG")
        assertNull(request.discoveredRetry(listOf(fullId, "ABCDEFG" + "B".repeat(45))))
        assertEquals(fullId, request.discoveredRetry(listOf(fullId)))
    }

    @Test
    fun fullCodesAndIncompleteInputNeverAcquireALocalRetry() {
        for (text in listOf(fullId + "?addr=tcp://192.168.1.4:22000", "ABC", "ABC EFG")) {
            val request = PairingRequest(text)
            assertEquals(text, request.text)
            assertNull(request.localCode)
            assertNull(request.discoveredRetry(listOf(fullId)))
        }
    }
}
