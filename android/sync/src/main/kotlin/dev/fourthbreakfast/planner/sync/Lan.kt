package dev.fourthbreakfast.planner.sync

import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** This device's addresses on its local networks, as Syncthing addresses on [port]: what another device can dial. */
fun lanAddresses(port: Int): List<String> = lanInterfaces().map { "tcp://${it.address.hostAddress}:$port" }

private fun lanInterfaces() = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.interfaceAddresses }
        .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
}.getOrDefault(emptyList())

/**
 * Finds the device whose id starts with [shortCode] on the local networks by asking every address on them, for when
 * the broadcasts devices normally find each other with do not get through, as on some wifi. Returns the device's id
 * and address, or null. Only networks of up to 256 addresses are tried.
 */
suspend fun findOnLan(shortCode: String, port: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
    val wanted = shortCode.replace("-", "").take(7).uppercase()
    val hosts = lanInterfaces().filter { it.networkPrefixLength >= 24 }.flatMap { own ->
        val mine = own.address.address
        val base = mine.copyOf()
        (1..254).mapNotNull { last ->
            base[3] = last.toByte()
            Inet4Address.getByAddress(base).hostAddress.takeIf { it != own.address.hostAddress }
        }
    }.distinct()
    coroutineScope {
        val atOnce = Semaphore(48)
        hosts.map { host ->
            async {
                atOnce.withPermit {
                    deviceIdAt(host, port)?.takeIf { shortId(it) == wanted }?.let { it to "tcp://$host:$port" }
                }
            }
        }.awaitAll().firstOrNull { it != null }
    }
}

/**
 * What one device's code carries to another: its id, and the addresses it can be dialed at, so pairing works even
 * where devices cannot find each other by themselves.
 */
data class PairCode(val id: String, val addresses: List<String>)

fun pairText(id: String, addresses: List<String>): String = if (addresses.isEmpty()) id else id + "?addr=" + addresses.joinToString(",")

/** Reads a code typed or scanned: a whole id with or without addresses, or just the first letters of one. */
fun parsePairCode(text: String): PairCode {
    val trimmed = text.trim()
    val id = trimmed.substringBefore("?").uppercase()
    val addresses = trimmed.substringAfter("?addr=", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return PairCode(id, addresses)
}
