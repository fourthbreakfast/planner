package dev.fourthbreakfast.planner.updates

import dev.fourthbreakfast.planner.ui.Prefs
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val content = "installer fixture".toByteArray()
    private val checksum = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    private fun metadata(digest: String? = "sha256:$checksum", url: String = "https://example.test/installer.apk") =
        """{"tag_name":"v2.0.0", "assets":[{"name":"planner.apk", "digest":${digest?.let { "\"$it\"" } ?: "null"}, "browser_download_url":"$url"}]}"""

    private fun updates(folder: File, prefs: Prefs = MemoryUpdatePrefs(), transport: UpdateTransport) =
        Updates(prefs, "1.0.0", folder, { it.apk }, { 2 * 24 * 60 * 60 * 1000L }, transport)

    @Test
    fun verifiedInstallerUsesASafeLocalNameAndPreservesUnrelatedFiles() = runBlocking {
        val folder = temporary.newFolder()
        val unrelated = File(folder, "keep.txt").apply { writeText("keep") }
        val updates = updates(folder) { _, json ->
            if (json) metadata(url = "https://example.test/../installer.apk?name=../../outside.exe").byteInputStream() else content.inputStream()
        }
        updates.check()
        val ready = updates.state.value as UpdateState.Ready
        assertEquals(folder.canonicalFile, ready.file.parentFile.canonicalFile)
        assertTrue(ready.file.name.matches(Regex("planner-update-[0-9]+\\.apk")))
        assertArrayEquals(content, ready.file.readBytes())
        assertTrue(unrelated.exists())
        assertEquals(2, folder.listFiles()!!.size)
    }

    @Test
    fun missingOrInvalidChecksumsFailBeforeDownloading() = runBlocking {
        for (digest in listOf(null, "sha256:bad", "sha512:$checksum")) {
            val folder = temporary.newFolder()
            var downloads = 0
            val updates = updates(folder) { _, json ->
                if (json) {
                    metadata(digest).byteInputStream()
                } else {
                    downloads++
                    content.inputStream()
                }
            }
            updates.check()
            assertEquals(UpdateState.Failed, updates.state.value)
            assertEquals(0, downloads)
            assertTrue(folder.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun mismatchAndInterruptedDownloadsLeaveNoInstallerAndCanBeRetried() = runBlocking {
        val folder = temporary.newFolder()
        var attempt = 0
        var closed = false
        val updates = updates(folder) { _, json ->
            if (json) {
                metadata().byteInputStream()
            } else {
                when (attempt++) {
                    0 -> "corrupt".byteInputStream()
                    1 -> object : InputStream() {
                        override fun read(): Int = throw IOException("connection lost")

                        override fun close() {
                            closed = true
                        }
                    }
                    else -> content.inputStream()
                }
            }
        }
        repeat(2) {
            updates.check()
            assertEquals(UpdateState.Failed, updates.state.value)
            assertTrue(folder.listFiles()!!.isEmpty())
        }
        assertTrue(closed)
        updates.check()
        assertTrue(updates.state.value is UpdateState.Ready)
    }

    @Test
    fun installersAreReadInBoundedChunks() = runBlocking {
        val folder = temporary.newFolder()
        val size = 16 * 1024 * 1024
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(size / 4096) { digest.update(ByteArray(4096) { 42 }) }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        var closed = false
        val updates = updates(folder) { _, json ->
            if (json) {
                metadata("sha256:$hash").byteInputStream()
            } else {
                object : InputStream() {
                    var remaining = size

                    override fun read(): Int = error("bulk reads required")

                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        assertTrue(length <= 8192)
                        if (remaining == 0) return -1
                        val count = minOf(remaining, length)
                        bytes.fill(42, offset, offset + count)
                        remaining -= count
                        return count
                    }

                    override fun close() {
                        closed = true
                    }
                }
            }
        }
        updates.check()
        assertEquals(size.toLong(), (updates.state.value as UpdateState.Ready).file.length())
        assertTrue(closed)
    }

    @Test(timeout = 10_000)
    fun concurrentChecksDoNotStartASecondRequest() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val updates = updates(temporary.newFolder()) { _, json ->
            requests.incrementAndGet()
            if (json) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                metadata().byteInputStream()
            } else {
                content.inputStream()
            }
        }
        val first = launch(Dispatchers.Default) { updates.check() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            updates.check()
            updates.checkIfDue()
            assertEquals(1, requests.get())
        } finally {
            release.countDown()
            first.join()
        }
        assertEquals(2, requests.get())
        assertTrue(updates.state.value is UpdateState.Ready)
    }

    @Test(timeout = 10_000)
    fun cancellationDeletesPartialFilesAndAllowsRetry() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val folder = temporary.newFolder()
        var interrupted = true
        var closed = false
        val updates = updates(folder) { _, json ->
            if (json) {
                metadata().byteInputStream()
            } else if (interrupted) {
                object : InputStream() {
                    override fun read(): Int {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        return 42
                    }

                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        bytes[offset] = read().toByte()
                        return 1
                    }

                    override fun close() {
                        closed = true
                    }
                }
            } else {
                content.inputStream()
            }
        }
        val first = launch(Dispatchers.Default) { updates.check() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            first.cancel()
        } finally {
            release.countDown()
            first.cancelAndJoin()
        }
        assertEquals(UpdateState.Unknown, updates.state.value)
        assertTrue(closed)
        assertTrue(folder.listFiles()!!.isEmpty())
        interrupted = false
        updates.check()
        assertTrue(updates.state.value is UpdateState.Ready)
    }

    @Test
    fun malformedOrOversizedMetadataFailsWithoutStartingDownload() = runBlocking {
        for (body in listOf("""{"tag_name":{}, "assets":[]}""", " ".repeat(1024 * 1024 + 1))) {
            val updates = updates(temporary.newFolder()) { _, json ->
                assertTrue(json)
                body.byteInputStream()
            }
            updates.check()
            assertEquals(UpdateState.Failed, updates.state.value)
        }
    }

    @Test
    fun oversizedInstallerStopsReadingAndDeletesThePartialFile() = runBlocking {
        val folder = temporary.newFolder()
        var closed = false
        val transport = UpdateTransport { _, json ->
            if (json) {
                metadata().byteInputStream()
            } else {
                object : InputStream() {
                    override fun read(): Int = 42

                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        bytes.fill(42, offset, offset + length)
                        return length
                    }

                    override fun close() {
                        closed = true
                    }
                }
            }
        }
        val updates = Updates(MemoryUpdatePrefs(), "1.0.0", folder, { it.apk }, { 0L }, transport, maxInstallerBytes = 16)
        updates.check()
        assertEquals(UpdateState.Failed, updates.state.value)
        assertTrue(folder.listFiles()!!.isEmpty())
        assertTrue(closed)
    }

    @Test
    fun aNewerReleaseWithoutAValidPlatformAssetFailsInsteadOfClaimingUpToDate() = runBlocking {
        val updates = updates(temporary.newFolder()) { _, json ->
            assertTrue(json)
            metadata(url = "http://example.test/installer.apk").byteInputStream()
        }
        updates.check()
        assertEquals(UpdateState.Failed, updates.state.value)
    }

    @Test
    fun dailyChecksRespectPreferencesAndTheLastAttempt() = runBlocking {
        val prefs = MemoryUpdatePrefs()
        var requests = 0
        val updates = updates(temporary.newFolder(), prefs) { _, _ ->
            requests++
            throw IOException("offline")
        }
        updates.daily = false
        updates.checkIfDue()
        assertEquals(0, requests)
        updates.daily = true
        updates.checkIfDue()
        updates.checkIfDue()
        assertEquals(1, requests)
        assertFalse(updates.state.value is UpdateState.Ready)
    }
}

private class MemoryUpdatePrefs : Prefs {
    private val values = HashMap<String, Any?>()

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = values.set(key, value)

    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) = values.set(key, value)

    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default

    override fun putLong(key: String, value: Long) = values.set(key, value)

    override fun getString(key: String) = values[key] as? String

    override fun putString(key: String, value: String?) = values.set(key, value)
}
