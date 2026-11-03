package dev.fourthbreakfast.planner.sync

/**
 * The ports this app's Syncthing listens and announces on. They differ from Syncthing's own (22000 and 21027), so a
 * Syncthing the person runs for themselves never finds this one, and this one only finds other copies of the app.
 */
data class Ports(val listen: Int = 22101, val announce: Int = 21101)

private const val RELAYS = "dynamic+https://relays.syncthing.net/endpoint"

/**
 * A Syncthing config file with this app's options: its own ports, and nothing sent to anyone outside the local network
 * unless [anywhere] is on. Without it there is no global discovery, no relays, no router port mapping and no STUN.
 * Usage reports, crash reports, upgrades and the browser are always off.
 */
fun plannerConfig(xml: String, ports: Ports, anywhere: Boolean): String {
    val options = linkedMapOf(
        "globalAnnounceEnabled" to "$anywhere",
        "localAnnounceEnabled" to "true",
        "localAnnouncePort" to "${ports.announce}",
        "localAnnounceMCAddr" to "[ff12::8384]:${ports.announce}",
        "relaysEnabled" to "$anywhere",
        "natEnabled" to "$anywhere",
        "stunKeepaliveStartS" to if (anywhere) "180" else "0",
        "startBrowser" to "false",
        "urAccepted" to "-1",
        "autoUpgradeIntervalH" to "0",
        "crashReportingEnabled" to "false",
    )
    var out = xml
    for ((tag, value) in options) {
        val pattern = Regex("<$tag>[^<]*</$tag>")
        out = if (pattern.containsMatchIn(out)) {
            pattern.replace(out, "<$tag>$value</$tag>")
        } else {
            out.replaceFirst("</options>", "    <$tag>$value</$tag>\n    </options>")
        }
    }
    val listen = listOf("tcp://0.0.0.0:${ports.listen}", "quic://0.0.0.0:${ports.listen}") + if (anywhere) listOf(RELAYS) else emptyList()
    out = Regex("\\s*<listenAddress>[^<]*</listenAddress>").replace(out, "")
    return out.replaceFirst("<options>", "<options>" + listen.joinToString("") { "\n        <listenAddress>$it</listenAddress>" })
}
