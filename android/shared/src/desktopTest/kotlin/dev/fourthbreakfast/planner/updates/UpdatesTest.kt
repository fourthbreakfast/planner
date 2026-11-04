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
        val json = """
            {"tag_name": "v0.9.0", "assets": [
              {"name": "planner-0.9.0.apk", "digest": "sha256:ABC", "browser_download_url": "https://github.com/x/releases/download/v0.9.0/planner-0.9.0.apk"},
              {"name": "planner-0.9.0.msi", "browser_download_url": "https://github.com/x/releases/download/v0.9.0/planner-0.9.0.msi"}
            ]}
        """.trimIndent()
        val release = parseRelease(json)!!
        assertEquals("0.9.0", release.version)
        assertTrue(release.apk!!.url.endsWith(".apk"))
        assertEquals("ABC", release.apk!!.sha256)
        assertTrue(release.msi!!.url.endsWith(".msi"))
        assertNull(release.msi!!.sha256)
    }

    @Test
    fun aFileNotOfferedOverHttpsIsLeftOut() {
        val json = """{"tag_name": "v0.9.0", "assets": [{"name": "planner.apk", "browser_download_url": "file:///somewhere/planner.apk"}]}"""
        assertNull(parseRelease(json)!!.apk)
    }

    @Test
    fun somethingElseIsNoRelease() {
        assertNull(parseRelease("""{"message": "Not Found"}"""))
        assertNull(parseRelease("not json"))
    }
}
