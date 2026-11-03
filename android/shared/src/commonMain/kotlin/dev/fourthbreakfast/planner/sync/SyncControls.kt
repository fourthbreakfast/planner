package dev.fourthbreakfast.planner.sync

import kotlinx.coroutines.flow.StateFlow

/** What the sync screen shows. */
data class SyncState(
    val running: Boolean = false,
    val myId: String = "",
    val peers: List<Peer> = emptyList(),
    val nearby: List<String> = emptyList(),
    /** Devices asking to sync, waiting for a yes or a no. */
    val asking: List<Peer> = emptyList(),
    val failed: Boolean = false,
)

/** What the sync screen can ask of the device's sync, which runs differently on a phone and a computer. */
interface SyncControls {
    val state: StateFlow<SyncState>

    /** False where the bundled Syncthing was not built for this device. */
    val available: Boolean

    val anywhere: Boolean

    /** The sync screen is open: sync runs, and devices asking to pair are shown, to be let in or turned down. */
    fun openPairing()

    fun closePairing()

    fun chooseAnywhere(on: Boolean)

    /** Pairs with a device by its whole id, or by the short code it shows when it is on the same network. */
    suspend fun pairWith(code: String): Boolean

    fun letIn(id: String)

    fun refuse(id: String)

    fun unpair(id: String)
}
