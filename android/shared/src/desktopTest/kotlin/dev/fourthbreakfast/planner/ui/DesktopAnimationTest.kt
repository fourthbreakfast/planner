package dev.fourthbreakfast.planner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopAnimationTest {
    @Test
    fun windowsPreferenceAndFailuresAreRespected() {
        assertFalse(desktopAnimationsOn("Windows 11") { false })
        assertTrue(desktopAnimationsOn("Windows 11") { true })
        assertTrue(desktopAnimationsOn("Windows 11") { null })
        assertTrue(desktopAnimationsOn("Windows 11") { throw UnsatisfiedLinkError() })
    }

    @Test
    fun otherSystemsDoNotLoadWindowsApi() {
        assertTrue(desktopAnimationsOn("Linux") { error("must not call Windows") })
    }

    @Test
    fun windowsApiReturnsASetting() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assertNotNull(readWindowsAnimations())
    }
}
