package dev.fourthbreakfast.planner.notes

/** Serializes an editor's saves, coalescing edits made while a write is in flight. */
internal class NoteSaveQueue(initial: Pair<String, String>, initialVersion: Long, private val write: (String, String, Long, (Long?) -> Unit) -> Unit) {
    var saved = initial
        private set
    var version = initialVersion
        private set
    private var writing = false
    private var desired: Pair<String, String>? = null
    private var idleAction: (() -> Unit)? = null

    val idle: Boolean get() = !writing

    fun save(text: Pair<String, String>) {
        desired = text
        drain()
    }

    fun afterSaving(action: () -> Unit) {
        if (idle) action() else idleAction = action
    }

    fun acceptRemote(text: Pair<String, String>, remoteVersion: Long) {
        saved = text
        version = remoteVersion
    }

    private fun drain() {
        if (writing) return
        val next = desired
        desired = null
        if (next != null && next != saved) {
            writing = true
            write(next.first, next.second, version) { writtenVersion ->
                if (writtenVersion == null) {
                    desired = null
                } else {
                    saved = next
                    version = writtenVersion
                }
                writing = false
                drain()
            }
        } else {
            val action = idleAction
            idleAction = null
            action?.invoke()
        }
    }
}
