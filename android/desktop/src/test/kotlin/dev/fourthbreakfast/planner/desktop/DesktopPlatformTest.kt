package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlatformTest {
    private fun withHome(test: (File) -> Unit) {
        val home = Files.createTempDirectory("planner-platform-test").toFile()
        try {
            test(home)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun linuxUsesXdgDataAndPreservesEarlierSourceBuilds() = withHome { home ->
        val normal = File(home, ".local/share/planner")
        assertEquals(normal, desktopDataFolder("Linux", emptyMap(), home))
        assertEquals(normal, desktopDataFolder("Linux", mapOf("XDG_DATA_HOME" to "relative"), home))
        val xdg = File(home, "custom-data")
        assertEquals(File(xdg, "planner"), desktopDataFolder("Linux", mapOf("XDG_DATA_HOME" to xdg.path), home))
        val legacy = File(home, ".config/planner").apply { mkdirs() }
        File(legacy, "planner.db").writeText("existing database")
        assertEquals(legacy, desktopDataFolder("Linux", emptyMap(), home))
        normal.mkdirs()
        File(normal, "planner.db").writeText("new location already in use")
        assertEquals(normal, desktopDataFolder("Linux", emptyMap(), home))
    }

    @Test
    fun windowsAndTrialRunsKeepTheirOwnLocations() = withHome { home ->
        val roaming = File(home, "roaming")
        assertEquals(File(roaming, "planner"), desktopDataFolder("Windows 11", mapOf("APPDATA" to roaming.path), home))
        val trial = File(home, "trial")
        assertEquals(trial, desktopDataFolder("Linux", mapOf("PLANNER_DATA" to trial.path), home))
    }

    @Test
    fun aLinuxUpdateOpensTheVerifiedPackageWithoutAdministrativeCommands() {
        val deb = File("planner-update.deb").absoluteFile
        assertEquals(listOf("xdg-open", deb.path), desktopInstallerCommand(deb, "Linux"))
        assertThrows(IOException::class.java) { desktopInstallerCommand(File("planner-update.exe"), "Linux") }
        assertThrows(IOException::class.java) { desktopInstallerCommand(deb, "Windows 11") }
        assertThrows(IOException::class.java) { desktopInstallerCommand(deb, "Mac OS X") }
        val setup = File("planner-setup.exe").absoluteFile
        assertEquals(listOf(setup.path, "/SILENT", "/NORESTART", "/SUPPRESSMSGBOXES"), desktopInstallerCommand(setup, "Windows 11"))
        assertEquals("syncthing", desktopSyncthingName("Linux"))
        assertEquals("syncthing.exe", desktopSyncthingName("Windows 11"))
        assertEquals(null, desktopSyncthingName("Mac OS X"))
    }

    @Test
    fun orphanCleanupMatchesOnlyTheAppsExactSyncthingHome() = withHome { home ->
        val command = File(home, "syncthing").path
        assertTrue(isPrivateSyncthing(command, arrayOf("serve", "--home=${home.path}"), home))
        assertTrue(isPrivateSyncthing(command, arrayOf("serve", "--home", home.path), home))
        assertFalse(isPrivateSyncthing(command, arrayOf("serve", "--home=${home.path}-other"), home))
        assertFalse(isPrivateSyncthing(File(home, "other-program").path, arrayOf("--home=${home.path}"), home))
        assertFalse(isPrivateSyncthing(command, arrayOf("--home"), home))
        assertFalse(isPrivateSyncthing(command, null, home))
    }
}
