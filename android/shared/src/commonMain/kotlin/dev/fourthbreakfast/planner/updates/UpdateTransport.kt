package dev.fourthbreakfast.planner.updates

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

internal fun interface UpdateTransport {
    fun open(url: String, json: Boolean): InputStream
}

internal fun httpsUri(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf {
    it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrEmpty() && it.userInfo == null
}

/** Follows GitHub's asset redirects without allowing a redirect to downgrade transport security. */
internal class HttpsUpdateTransport(private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) : UpdateTransport {
    override fun open(url: String, json: Boolean): InputStream {
        var uri = httpsUri(url) ?: throw IOException("update URL must use HTTPS")
        repeat(6) { hop ->
            val connection = connect(uri)
            var streaming = false
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "planner")
                if (json) connection.setRequestProperty("Accept", "application/vnd.github+json")
                when (val status = connection.responseCode) {
                    200 -> {
                        val input = connection.inputStream
                        streaming = true
                        return object : FilterInputStream(input) {
                            override fun close() {
                                try {
                                    super.close()
                                } finally {
                                    connection.disconnect()
                                }
                            }
                        }
                    }
                    301, 302, 303, 307, 308 -> {
                        if (hop == 5) throw IOException("too many update redirects")
                        val location = connection.getHeaderField("Location") ?: throw IOException("missing update redirect")
                        val next = runCatching { uri.resolve(location).toString() }.getOrNull()
                        uri = next?.let(::httpsUri) ?: throw IOException("update redirect must use HTTPS")
                    }
                    else -> throw IOException("update server said $status")
                }
            } finally {
                if (!streaming) connection.disconnect()
            }
        }
        throw IOException("too many update redirects")
    }
}
