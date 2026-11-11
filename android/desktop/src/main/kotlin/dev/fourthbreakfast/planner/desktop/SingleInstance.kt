package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import kotlin.concurrent.thread

private const val LOCK = "planner.lock"
private const val OWNER = "planner.pid"
private const val SHOW = "show-window"

/**
 * One planner at a time on a computer: two would open the same database and start two Syncthings on the same folder.
 * The first one holds a lock in the data folder. A second one asks the first to come forward, by leaving a file it
 * watches for, and then quits.
 */
class SingleInstance(private val folder: File, private val allowForeground: (Long) -> Unit = WindowsIdentity::allowForeground) : AutoCloseable {
    private var lock: FileLock? = null
    private var channel: FileChannel? = null
    private var watcher: WatchService? = null

    /** Takes the lock, and returns false when another planner already has it (and has been asked to show itself). */
    fun claim(): Boolean {
        folder.mkdirs()
        val opened = RandomAccessFile(File(folder, LOCK), "rw").channel
        lock = runCatching { opened.tryLock() }.getOrNull()
        if (lock == null) {
            opened.close()
            runCatching { File(folder, OWNER).readText().trim().toLongOrNull() }.getOrNull()?.let(allowForeground)
            File(folder, SHOW).writeText("")
        } else {
            channel = opened
            // Windows makes the held lock mandatory: another process cannot read PID text from that file.
            // Keep ownership metadata readable so a second launch can transfer its foreground permission.
            File(folder, OWNER).writeText(ProcessHandle.current().pid().toString())
        }
        return lock != null
    }

    /** Calls [onShow] whenever another start asks this one to come forward. */
    fun listen(onShow: () -> Unit) {
        val watcher = FileSystems.getDefault().newWatchService().also { this.watcher = it }
        folder.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
        // A second launch can arrive before the first window is ready. Do not discard that request.
        if (File(folder, SHOW).exists()) {
            File(folder, SHOW).delete()
            onShow()
        }
        thread(isDaemon = true, name = "planner single instance") {
            while (true) {
                val key = runCatching { watcher.take() }.getOrNull() ?: return@thread
                val asked = key.pollEvents().any { it.context().toString() == SHOW }
                key.reset()
                if (asked) {
                    File(folder, SHOW).delete()
                    onShow()
                }
            }
        }
    }

    override fun close() {
        watcher?.close()
        watcher = null
        if (lock != null) File(folder, OWNER).delete()
        lock?.release()
        lock = null
        channel?.close()
        channel = null
    }
}
