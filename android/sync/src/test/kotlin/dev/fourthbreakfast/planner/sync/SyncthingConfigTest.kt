package dev.fourthbreakfast.planner.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncthingConfigTest {
    private val generated = """
        <configuration version="51">
            <options>
                <listenAddress>default</listenAddress>
                <globalAnnounceServer>default</globalAnnounceServer>
                <globalAnnounceEnabled>true</globalAnnounceEnabled>
                <localAnnounceEnabled>true</localAnnounceEnabled>
                <localAnnouncePort>21027</localAnnouncePort>
                <localAnnounceMCAddr>[ff12::8384]:21027</localAnnounceMCAddr>
                <relaysEnabled>true</relaysEnabled>
                <startBrowser>true</startBrowser>
                <natEnabled>true</natEnabled>
                <urAccepted>0</urAccepted>
                <autoUpgradeIntervalH>12</autoUpgradeIntervalH>
                <crashReportingEnabled>true</crashReportingEnabled>
                <stunKeepaliveStartS>180</stunKeepaliveStartS>
            </options>
        </configuration>
    """.trimIndent()

    private fun value(xml: String, tag: String) = Regex("<$tag>([^<]*)</$tag>").findAll(xml).map { it.groupValues[1] }.toList()

    @Test
    fun onTheLocalNetworkNothingGoesOut() {
        val xml = plannerConfig(generated, Ports(22101, 21101), anywhere = false)
        assertEquals(listOf("false"), value(xml, "globalAnnounceEnabled"))
        assertEquals(listOf("false"), value(xml, "relaysEnabled"))
        assertEquals(listOf("false"), value(xml, "natEnabled"))
        assertEquals(listOf("0"), value(xml, "stunKeepaliveStartS"))
        assertEquals(listOf("-1"), value(xml, "urAccepted"))
        assertEquals(listOf("false"), value(xml, "crashReportingEnabled"))
        assertEquals(listOf("0"), value(xml, "autoUpgradeIntervalH"))
        assertEquals(listOf("false"), value(xml, "startBrowser"))
        assertEquals(listOf("tcp://0.0.0.0:22101", "quic://0.0.0.0:22101"), value(xml, "listenAddress"))
        assertEquals(listOf("21101"), value(xml, "localAnnouncePort"))
        assertEquals(listOf("[ff12::8384]:21101"), value(xml, "localAnnounceMCAddr"))
    }

    @Test
    fun anywhereTurnsOnDiscoveryAndRelays() {
        val xml = plannerConfig(generated, Ports(), anywhere = true)
        assertEquals(listOf("true"), value(xml, "globalAnnounceEnabled"))
        assertEquals(listOf("true"), value(xml, "relaysEnabled"))
        assertTrue(value(xml, "listenAddress").any { it.startsWith("dynamic+https://relays") })
        // reports and upgrades stay off either way
        assertEquals(listOf("-1"), value(xml, "urAccepted"))
        assertEquals(listOf("false"), value(xml, "crashReportingEnabled"))
    }

    @Test
    fun applyingTwiceGivesTheSameFile() {
        val once = plannerConfig(generated, Ports(), anywhere = true)
        assertEquals(once, plannerConfig(once, Ports(), anywhere = true))
        val back = plannerConfig(once, Ports(), anywhere = false)
        assertFalse(value(back, "listenAddress").any { it.startsWith("dynamic") })
    }

    @Test
    fun theShortIdIsTheFirstSevenLetters() {
        assertEquals("G24P74E", shortId("G24P74E-4L44OVF-MNYDXUX-VBFKUOO-PDV5EK3-72DXSGH-64OUJWX-VPPSEAT"))
    }
}
