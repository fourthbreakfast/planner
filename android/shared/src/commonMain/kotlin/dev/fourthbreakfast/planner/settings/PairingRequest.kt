package dev.fourthbreakfast.planner.settings

import dev.fourthbreakfast.planner.sync.shortId

/** An explicitly submitted code. Merely editing the field must never authorize pairing. */
internal class PairingRequest(val text: String) {
    private val id = text.trim().substringBefore('?').replace("-", "").uppercase()
    val localCode: String? = id.takeIf { it.length == 7 && it.all { character -> character.isLetterOrDigit() } }
    private val retried = mutableSetOf<String>()

    /** Retry a failed local code once when discovery supplies its unambiguous full device ID. */
    fun discoveredRetry(nearby: List<String>): String? {
        val code = localCode ?: return null
        val matching = nearby.distinct().filter { it.replace("-", "").length >= 52 && shortId(it) == code }
        val fullId = matching.singleOrNull() ?: return null
        return fullId.takeIf { retried.add(it) }
    }
}
