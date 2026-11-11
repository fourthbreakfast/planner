package dev.fourthbreakfast.planner.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun versionsCompareByNumberNotByText() {
        assertTrue(isNewer("0.10.0", "0.9.0"))
        assertTrue(isNewer("v1.0.0", "0.8.0"))
        assertTrue(isNewer("0.8.1", "0.8.0"))
        assertFalse(isNewer("0.8.0", "0.8.0"))
        assertFalse(isNewer("0.7.9", "0.8.0"))
        assertFalse(isNewer("0.8", "0.8.0"))
    }

    @Test
    fun aReleasePageGivesTheVersionAndBothFiles() {
        val digest = "ABC1".repeat(16)
        val json = """
            {"tag_name": "v0.9.0", "assets": [
              {"name": "planner-0.9.0.apk", "digest": "sha256:$digest", "browser_download_url": "https://github.com/x/releases/download/v0.9.0/planner-0.9.0.apk"},
              {"name": "planner-0.9.0.msi", "browser_download_url": "https://github.com/x/releases/download/v0.9.0/planner-0.9.0.msi"},
              {"name": "planner-0.9.0-setup.exe", "browser_download_url": "https://github.com/x/releases/download/v0.9.0/planner-0.9.0-setup.exe"}
            ]}
        """.trimIndent()
        val release = parseRelease(json)!!
        assertEquals("0.9.0", release.version)
        assertTrue(release.apk!!.url.endsWith(".apk"))
        assertEquals(digest, release.apk!!.sha256)
        assertTrue(release.msi!!.url.endsWith(".msi"))
        assertNull(release.msi!!.sha256)
        assertTrue(release.setup!!.url.endsWith("-setup.exe"))
    }

    @Test
    fun aFileNotOfferedOverHttpsIsLeftOut() {
        val json = """{"tag_name": "v0.9.0", "assets": [{"name": "planner.apk", "browser_download_url": "file:///somewhere/planner.apk"}]}"""
        assertNull(parseRelease(json)!!.apk)
    }

    @Test
    fun linuxInstallersAreRecognizedSeparatelyFromWindowsSetup() {
        val digest = "ab".repeat(32)
        val release = parseRelease(
            """{"tag_name":"v2.0.0","assets":[
              {"name":"planner_2.0.0_amd64.deb","digest":"sha256:$digest","browser_download_url":"https://github.com/x/planner.deb"}
            ]}""",
        )!!
        assertEquals("https://github.com/x/planner.deb", release.deb?.url)
        assertEquals(digest, release.deb?.sha256)
        assertNull(release.setup)
    }

    @Test
    fun somethingElseIsNoRelease() {
        assertNull(parseRelease("""{"message": "Not Found"}"""))
        assertNull(parseRelease("not json"))
    }

    @Test
    fun unexpectedJsonShapesNeverCrashTheUpdater() {
        listOf(
            "[]",
            "null",
            """{"tag_name": [], "assets": []}""",
            """{"tag_name": 123, "assets": []}""",
            """{"tag_name": "v1", "assets": {}}""",
        ).forEach { assertNull(parseRelease(it)) }
        val release = parseRelease(
            """{"tag_name":"v2", "assets":[null, 4, [], {"name":{}}, {"name":"planner.apk", "browser_download_url":[]}]}""",
        )!!
        assertNull(release.apk)
    }

    @Test
    fun malformedChecksumsAreNotTreatedAsPublishedChecksums() {
        listOf("sha256:ABC", "sha512:${"a".repeat(64)}", "sha256:${"g".repeat(64)}").forEach { digest ->
            val release = parseRelease(
                """{"tag_name":"v2", "assets":[{"name":"planner.apk", "digest":"$digest", "browser_download_url":"https://example.test/a.apk"}]}""",
            )!!
            assertNull(release.apk!!.sha256)
        }
    }
}
