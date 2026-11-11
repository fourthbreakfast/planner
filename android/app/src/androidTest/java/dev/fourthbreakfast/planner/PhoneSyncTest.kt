package dev.fourthbreakfast.planner

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.fourthbreakfast.planner.core.Task
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real ARM64 tests. Run selected methods directly with adb; never uninstall or use connectedAndroidTest on a phone. */
@RunWith(AndroidJUnit4::class)
class PhoneSyncTest {
    private class Run(val app: PlannerApp, val token: String, val endpoint: String, val headless: Boolean, val requeue: Boolean) {
        val prefix = "planner QA $token"
        val pcId = "qa-$token-pc"
        val backgroundId = "qa-$token-background"
        val directId = "qa-$token-direct"
        val phoneTitle = "$prefix phone"

        fun report(stage: String, detail: String = "") {
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply {
                    putString("plannerQaStage", stage)
                    putString("plannerQaRun", token)
                    putString("plannerQaDetail", detail)
                },
            )
        }

        fun control(action: String, data: JSONObject? = null): JSONObject {
            val connection = URI("$endpoint/$action").toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 30_000
                if (data != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(data.toString().toByteArray()) }
                }
                assertEquals("Host control failed: $action", 200, connection.responseCode)
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

        suspend fun task(id: String): Task? = app.data.tasks.all.first().firstOrNull { it.id == id }

        suspend fun foregroundUiReady() {
            val marker = File(app.filesDir, "qa-ui-ready-$token")
            marker.delete()
            report(
                "open_sync_screen",
                "When authorized, open the QA Sync screen and touch files/${marker.name} using run-as. The test does not launch an Activity.",
            )
            await("operator confirms the actual Sync screen", 10 * 60_000L) { marker.isFile && isStarted() }
        }

        fun isStarted(): Boolean {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                visible = listOf(Stage.STARTED, Stage.RESUMED, Stage.PAUSED).any { stage ->
                    monitor.getActivitiesInStage(stage).any { it is MainActivity }
                }
            }
            return visible
        }

        suspend fun await(label: String, timeout: Long = 120_000, check: suspend () -> Boolean) {
            report("waiting", label)
            withTimeout(timeout) { while (!check()) delay(500) }
        }
    }

    private fun run(): Run {
        val arguments = InstrumentationRegistry.getArguments()
        val token = arguments.getString("plannerQaRun")
        val endpoint = arguments.getString("plannerQaControl")
        assumeTrue("Real-phone sync requires explicit QA arguments", token != null && endpoint != null)
        require(token!!.matches(Regex("[A-Za-z0-9_-]{8,64}")))
        val uri = URI(endpoint!!)
        require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.path == "/qa/$token")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PlannerApp
        assertEquals("This driver must never access regular or existing demo data", "dev.fourthbreakfast.planner.demo.qa", app.packageName)
        assertTrue("Real native sync needs ARM64", "arm64-v8a" in Build.SUPPORTED_ABIS)
        if (Build.VERSION.SDK_INT >= 37) {
            assertEquals(
                "Allow local-network access through the QA app UI first",
                PackageManager.PERMISSION_GRANTED,
                app.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK"),
            )
        }
        assertTrue("Bundled ARM64 Syncthing is missing", app.syncController.available)
        val headless = arguments.getString("plannerQaHeadless") == "true"
        val requeue = arguments.getString("plannerQaRequeuePeriodic") == "true"
        require(!requeue || headless) { "Periodic QA requeue is available only in explicit headless mode" }
        return Run(app, token, endpoint, headless, requeue)
    }

    @Test
    fun foregroundExchangeAndPrepareBackground() = runBlocking(Dispatchers.IO) {
        val qa = run()
        val sync = qa.app.syncController
        val baseline = qa.app.data.tasks.all.first().associateBy(Task::id)
        try {
            val host = qa.control("host")
            val hostId = host.getString("hostId")
            if (qa.headless) {
                qa.report("headless-exchange", "No Activity or foreground lifecycle callback will be started by this exchange.")
            } else {
                qa.foregroundUiReady()
            }
            sync.openPairing()
            sync.syncNow()
            assertTrue("Native Syncthing did not start: ${sync.state.value}", sync.state.value.running)
            assertFalse(sync.state.value.failed)
            assertTrue("QA must use its private listen port", sync.state.value.addresses.any { it.endsWith(":22103") })
            val previousHosts = sync.state.value.peers.filter { it.id != hostId && it.name.startsWith("planner QA PC ") }.map { it.id }
            for (id in previousHosts) sync.unpair(id)
            qa.await("previous named QA fixtures are unpaired") { sync.state.value.peers.none { it.id in previousHosts } }
            qa.control("phone", JSONObject().put("phoneId", sync.state.value.myId).put("exchangeMode", if (qa.headless) "headless" else "foreground"))
            assertTrue("Full device-code pairing failed", sync.pairWith(hostId))
            qa.await("connected to isolated host") {
                sync.syncNow()
                sync.state.value.peers.any { it.id == hostId && it.connected }
            }
            qa.await("host task merged into Room") {
                sync.syncNow()
                qa.task(qa.pcId)?.title == "${qa.prefix} pc"
            }
            qa.app.data.tasks.setDone(qa.pcId, true)
            qa.app.data.tasks.add(qa.phoneTitle, starred = true)
            qa.await("host verified phone task and shared completion") {
                sync.syncNow()
                qa.task("qa-${qa.token}-ack") != null
            }
            qa.await("sync status reports current") {
                sync.syncNow()
                sync.state.value.upToDate
            }
            if (qa.headless) {
                sync.closePairing()
                sync.stop()
                assertFalse(sync.state.value.running)
                qa.report("headless-exchange", "Two-way exchange verified; native explicitly stopped. No Home/linger assertion is made.")
            } else {
                qa.report("background_wait", "Press Home now; waiting for the actual foreground linger to stop native sync.")
                qa.await("actual Activity leaves the foreground", 60_000) { !qa.isStarted() }
                qa.await("native foreground linger stops", 100_000) { !sync.state.value.running }
                sync.closePairing()
            }
            val manager = WorkManager.getInstance(qa.app)
            val work = manager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS)
            assertTrue("Pairing must schedule periodic background sync", work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })
            qa.report("background-work", work.joinToString { "${it.id}:${it.state}" })
            val previousIds = work.map { it.id }.toSet()
            if (qa.requeue) {
                manager.cancelUniqueWork("sync").result.get(10, TimeUnit.SECONDS)
                val cancelled = manager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS).filter { it.id in previousIds }
                assertTrue(
                    "The old QA periodic request must be cancelled before rescheduling",
                    cancelled.isNotEmpty() && cancelled.all { it.state == WorkInfo.State.CANCELLED },
                )
            }
            assertEquals("The fresh background task must not predate the background stage", null, qa.task(qa.backgroundId))
            qa.control("background-ready", JSONObject().put("periodicRequeued", qa.requeue))
            if (qa.requeue) {
                sync.chooseAnywhere(sync.anywhere)
                qa.await("a fresh production periodic WorkSpec is scheduled") {
                    manager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS).any {
                        it.id !in previousIds && (it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING)
                    }
                }
                val replacement = manager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS).filter { it.id !in previousIds }
                qa.report(
                    "periodic-requeued",
                    replacement.joinToString { "${it.id}:${it.state}" } + "; first run is eligible; this is not a natural cadence claim",
                )
            }
            val after = qa.app.data.tasks.all.first().associateBy(Task::id)
            for ((id, before) in baseline) assertEquals("Existing QA task changed: $id", before, after[id])
            qa.report("background-ready", "Native stopped before fresh publication; unique WorkManager sync is scheduled. Trigger only this QA package's job.")
        } catch (failure: Throwable) {
            qa.report("failed", "${failure.javaClass.simpleName}: ${failure.message}; ${sync.state.value}")
            runCatching { qa.control("failed", JSONObject().put("message", failure.toString())) }
            sync.closePairing()
            sync.stop()
            throw failure
        }
    }

    @Test
    fun retainedPairingScreenStopsAndRestartsAcrossHome() = runBlocking(Dispatchers.IO) {
        val qa = run()
        val sync = qa.app.syncController
        qa.foregroundUiReady()
        sync.openPairing()
        sync.syncNow()
        qa.report("background_wait", "Press Home without navigating away from Sync; the pairing screen remains retained.")
        qa.await("actual Activity leaves the foreground", 60_000) { !qa.isStarted() }
        qa.await("retained pairing screen stops native in the background", 100_000) { !sync.state.value.running }
        qa.report("foreground_return", "Bring the QA app back to the foreground; the verifier will not call onInSight.")
        qa.await("actual Activity foreground return restarts native", 90_000) { sync.state.value.running }
        assertFalse(sync.state.value.failed)
        qa.report("lifecycle-passed", "Home stopped native and actual Activity return restarted it with the retained pairing screen.")
        sync.closePairing()
        sync.onOutOfSight()
        sync.stop()
    }

    @Test
    fun verifyScheduledBackgroundDelivery() = runBlocking(Dispatchers.IO) {
        val qa = run()
        // Reading Room must not call syncNow/onInSight/runOnce: only the previously scheduled worker may deliver this task.
        qa.await("scheduled worker committed the fresh background task", 15_000) { qa.task(qa.backgroundId) != null }
        assertEquals("${qa.prefix} background", qa.task(qa.backgroundId)?.title)
        assertTrue(qa.task(qa.pcId)?.done == true)
        assertTrue(qa.app.data.tasks.all.first().any { it.title == qa.phoneTitle && it.starred })
        qa.control("complete", JSONObject().put("mode", "scheduled-worker"))
        qa.report("passed", "Fresh background task was already committed in Room; verifier did not start native sync.")
    }

    @Test
    fun cleanupQaPeers() = runBlocking(Dispatchers.IO) {
        val qa = run()
        val sync = qa.app.syncController
        try {
            sync.openPairing()
            sync.syncNow()
            val baseline = qa.app.data.tasks.all.first().associateBy(Task::id)
            val targets = sync.state.value.peers.filter { it.name.startsWith("planner QA PC ") }.map { it.id }.toSet()
            for (id in targets) sync.unpair(id)
            qa.await("only named QA fixture peers are removed") { sync.state.value.peers.none { it.id in targets } }
            if (sync.state.value.peers.isEmpty()) {
                val manager = WorkManager.getInstance(qa.app)
                qa.await("QA periodic sync is cancelled after the last fixture peer is removed") {
                    manager.getWorkInfosForUniqueWork("sync").get(10, TimeUnit.SECONDS).all { it.state.isFinished }
                }
            }
            assertEquals("Peer cleanup must preserve task data", baseline, qa.app.data.tasks.all.first().associateBy(Task::id))
            qa.report("cleanup-passed", "Removed ${targets.size} named fixture peers; retained ${sync.state.value.peers.size} other peers; tasks preserved.")
        } finally {
            sync.closePairing()
            sync.stop()
        }
    }

    @Test
    fun directRunOnceFallback() = runBlocking(Dispatchers.IO) {
        val qa = run()
        val sync = qa.app.syncController
        sync.openPairing()
        sync.syncNow()
        sync.onOutOfSight()
        sync.stop()
        assertEquals(null, qa.task(qa.directId))
        qa.control("direct-background", JSONObject())
        sync.runOnce()
        assertEquals("${qa.prefix} direct", qa.task(qa.directId)?.title)
        assertFalse("Background runOnce must stop native sync when the app is out of sight", sync.state.value.running)
        assertFalse(sync.state.value.failed)
        sync.closePairing()
        qa.control("complete", JSONObject().put("mode", "direct-run-once"))
        qa.report("passed-direct-only", "Direct runOnce delivered the task; this does not establish OS scheduling.")
    }
}
