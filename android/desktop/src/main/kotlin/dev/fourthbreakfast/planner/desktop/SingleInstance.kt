package dev.fourthbreakfast.planner.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import kotlin.concurrent.thread

private const val LOCK = "planner.lock"
private const val SHOW = "show-window"

/**
 * One planner at a time on a computer: two would open the same database and start two Syncthings on the same folder.
 * The first one holds a lock in the data folder. A second one asks the first to come forward, by leaving a file it
 * watches for, and then quits.
 */
class SingleInstance(private val folder: File) {
    private var lock: FileLock? = null

    /** Takes the lock, and returns false when another planner already has it (and has been asked to show itself). */
    fun claim(): Boolean {
        folder.mkdirs()
        lock = runCatching { RandomAccessFile(File(folder, LOCK), "rw").channel.tryLock() }.getOrNull()
        if (lock == null) File(folder, SHOW).writeText("")
        return lock != null
    }

    /** Calls [onShow] whenever another start asks this one to come forward. */
    fun listen(onShow: () -> Unit) {
        File(folder, SHOW).delete()
        val watcher = FileSystems.getDefault().newWatchService()
        folder.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
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
}
