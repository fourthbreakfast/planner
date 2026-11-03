package dev.fourthbreakfast.planner.sync

import dev.fourthbreakfast.planner.core.BackupException
import dev.fourthbreakfast.planner.core.Sync
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The files in the shared folder: one for each device, named by its id and written only by that device, so Syncthing
 * never has two versions of one file to choose between. This device writes its own with [publish] and takes in the
 * others with [takeIn].
 */
class DeviceFiles(private val folder: File, private val myId: String, private val sync: Sync) {
    private val seen = HashMap<String, Pair<Long, Long>>()
    private var written: String? = null

    /** Writes this device's file when what it holds has changed. */
    suspend fun publish() {
        val text = sync.snapshot()
        if (text == written) return
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            val temporary = File(folder, "$myId.json.tmp")
            temporary.writeText(text)
            val target = File(folder, "$myId.json")
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) throw IOException("could not write $target")
            }
        }
        written = text
    }

    /** Merges every other device's file that changed since it was last read, and returns how many things changed here. */
    suspend fun takeIn(): Int {
        val files = withContext(Dispatchers.IO) {
            folder.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") && it.name != "$myId.json" }
        }
        var changed = 0
        for (file in files) {
            val stamp = file.lastModified() to file.length()
            if (seen[file.name] == stamp) continue
            val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() } ?: continue
            try {
                changed += sync.merge(text)
            } catch (_: BackupException) {
                // half written, damaged, or from a newer version of the app: left for later
                continue
            }
            seen[file.name] = stamp
        }
        return changed
    }
}
