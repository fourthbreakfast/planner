package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SingleInstanceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun aSecondLaunchCanReadTheOwnersPidWhileTheExclusiveLockIsHeld() {
        val folder = temporary.newFolder("instance")
        var foregroundPid: Long? = null
        SingleInstance(folder).use { first ->
            assertTrue(first.claim())
            val expected = ProcessHandle.current().pid()
            assertEquals(expected.toString(), File(folder, "planner.pid").readText())
            val shown = CountDownLatch(1)
            first.listen { shown.countDown() }
            SingleInstance(folder) { foregroundPid = it }.use { second ->
                assertFalse(second.claim())
                assertEquals(expected, foregroundPid)
                assertTrue("The original instance must receive the window request", shown.await(3, TimeUnit.SECONDS))
                assertFalse(File(folder, "show-window").exists())
            }
        }
        SingleInstance(folder).use { replacement -> assertTrue(replacement.claim()) }
    }
}
