package dev.fourthbreakfast.planner.sync

import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/**
 * A Syncthing device id is its certificate: the sha256 of the certificate's bytes in base32, with a check letter after
 * every 13, in groups of 7. So any device that answers on the sync port tells its id just by starting a connection.
 */
fun deviceId(certificate: X509Certificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
    val raw = base32(digest)
    val withChecks = raw.chunked(13).joinToString("") { it + luhn(it) }
    return withChecks.chunked(7).joinToString("-")
}

private fun base32(bytes: ByteArray): String {
    val out = StringBuilder()
    var bits = 0
    var value = 0
    for (byte in bytes) {
        value = (value shl 8) or (byte.toInt() and 0xff)
        bits += 8
        while (bits >= 5) {
            out.append(BASE32[(value shr (bits - 5)) and 31])
            bits -= 5
        }
    }
    if (bits > 0) out.append(BASE32[(value shl (5 - bits)) and 31])
    return out.toString()
}

/** The check letter Syncthing adds to a group: luhn's sum over the base32 alphabet. */
private fun luhn(group: String): Char {
    var factor = 1
    var sum = 0
    for (i in group.indices.reversed()) {
        var addend = factor * BASE32.indexOf(group[i])
        factor = if (factor == 2) 1 else 2
        addend = addend / 32 + addend % 32
        sum += addend
    }
    return BASE32[(32 - sum % 32) % 32]
}

/**
 * The id of the device listening at [host]:[port], read from the certificate it shows when a connection starts, or
 * null when nothing answers there within [timeoutMillis]. The connection is dropped once the certificate is seen.
 */
fun deviceIdAt(host: String, port: Int, timeoutMillis: Int = 700, log: (String) -> Unit = {}): String? {
    var seen: X509Certificate? = null
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            seen = chain.firstOrNull()
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    return try {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        Socket().use { plain ->
            plain.connect(InetSocketAddress(host, port), timeoutMillis)
            plain.soTimeout = timeoutMillis * 3
            val tls = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
            // the other side wants a certificate from this one too, and may well refuse the handshake without it: the
            // certificate it sent first is all that is wanted here
            runCatching { tls.startHandshake() }.onFailure { log("$host handshake: $it") }
            runCatching { tls.close() }
        }
        seen?.let(::deviceId)
    } catch (e: Exception) {
        log("$host: $e")
        seen?.let(::deviceId)
    }
}
