package dev.fourthbreakfast.planner.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncHealthTest {
    private val phone = Peer("phone", "phone", true)
    private val laptop = Peer("laptop", "laptop", false)

    @Test
    fun oneConnectedDeviceCannotStandForEveryDevice() {
        val state = SyncState(running = true, peers = listOf(phone, laptop), upToDate = true)
        assertEquals(SyncHealth.OFFLINE, state.health)
        assertEquals(SyncHealth.CURRENT, state.copy(peers = listOf(phone, laptop.copy(connected = true))).health)
    }

    @Test
    fun connectionAloneDoesNotMeanDataHasArrived() {
        val state = SyncState(running = true, peers = listOf(phone))
        assertEquals(SyncHealth.WORKING, state.health)
        assertEquals(SyncHealth.CURRENT, state.copy(upToDate = true).health)
        assertEquals(SyncHealth.WORKING, state.copy(upToDate = true, syncing = true).health)
    }

    @Test
    fun stoppedOrFailedSyncNeverKeepsAGreenStatus() {
        val state = SyncState(running = true, peers = listOf(phone), upToDate = true)
        assertEquals(SyncHealth.OFFLINE, state.copy(running = false).health)
        assertEquals(SyncHealth.ERROR, state.copy(failed = true).health)
        assertEquals(SyncHealth.UNPAIRED, SyncState(upToDate = true).health)
    }
}
