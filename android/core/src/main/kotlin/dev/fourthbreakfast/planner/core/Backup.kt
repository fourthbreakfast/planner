package dev.fourthbreakfast.planner.core

/** A file that is not a planner backup, or is damaged. */
class BackupException(message: String) : Exception(message)

private const val BACKUP_VERSION = 1

/** Every task, the bin included, as a json file a person can read and keep. */
fun encodeBackup(tasks: List<Task>): String = buildString {
    append("{\n  \"app\": \"planner\",\n  \"version\": ").append(BACKUP_VERSION).append(",\n  \"tasks\": [")
    tasks.forEachIndexed { index, task ->
        if (index > 0) append(',')
        append("\n    {\"id\": ").append(quoted(task.id))
        append(", \"title\": ").append(quoted(task.title))
        append(", \"done\": ").append(task.done)
        append(", \"createdAt\": ").append(task.createdAt)
        append(", \"updatedAt\": ").append(task.updatedAt)
        append(", \"parentId\": ").append(task.parentId?.let(::quoted) ?: "null")
        append(", \"priority\": ").append(quoted(task.priority.name))
        append(", \"deletedAt\": ").append(task.deletedAt ?: "null")
        append(", \"position\": ").append(task.position)
        append('}')
    }
    append(if (tasks.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
}

/** The tasks in a backup, or a [BackupException] when the text is not one. */
fun decodeBackup(text: String): List<Task> {
    val root = JsonReader(text).read() as? Map<*, *> ?: throw BackupException("not a planner backup")
    if (root["app"] != "planner") throw BackupException("not a planner backup")
    val version = number(root["version"]).toInt()
    if (version > BACKUP_VERSION) throw BackupException("made by a newer version of planner")
    val tasks = root["tasks"] as? List<*> ?: throw BackupException("no tasks in this backup")
    return tasks.map { entry ->
        val task = entry as? Map<*, *> ?: throw BackupException("damaged task")
        val createdAt = number(task["createdAt"]).toLong()
        Task(
            id = task["id"] as? String ?: throw BackupException("a task has no id"),
            title = task["title"] as? String ?: throw BackupException("a task has no title"),
            done = task["done"] == true,
            createdAt = createdAt,
            updatedAt = task["updatedAt"]?.let { number(it).toLong() } ?: createdAt,
            parentId = task["parentId"] as? String,
            priority = Priority.entries.firstOrNull { it.name == task["priority"] } ?: Priority.NONE,
            deletedAt = task["deletedAt"]?.let { number(it).toLong() },
            position = task["position"]?.let { number(it).toDouble() } ?: 0.0,
        )
    }
}

private fun number(value: Any?): Number = value as? Number ?: throw BackupException("damaged number")

private fun quoted(text: String): String = buildString {
    append('"')
    text.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

/** Just enough json to read a backup back: objects, arrays, strings, numbers, true, false and null. */
private class JsonReader(private val text: String) {
    private var at = 0

    fun read(): Any? {
        val value = value()
        space()
        if (at != text.length) fail()
        return value
    }

    private fun fail(): Nothing = throw BackupException("not a planner backup")

    private fun space() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun value(): Any? {
        space()
        if (at >= text.length) fail()
        return when (text[at]) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> numberValue()
        }
    }

    private fun word(word: String, value: Any?): Any? {
        if (!text.startsWith(word, at)) fail()
        at += word.length
        return value
    }

    private fun numberValue(): Number {
        val start = at
        while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) at++
        val raw = text.substring(start, at)
        return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: fail()
    }

    private fun stringValue(): String {
        at++
        val result = StringBuilder()
        while (true) {
            if (at >= text.length) fail()
            val char = text[at++]
            when (char) {
                '"' -> return result.toString()
                '\\' -> {
                    if (at >= text.length) fail()
                    when (val escape = text[at++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'u' -> {
                            if (at + 4 > text.length) fail()
                            result.append((text.substring(at, at + 4).toIntOrNull(16) ?: fail()).toChar())
                            at += 4
                        }
                        else -> fail()
                    }
                }
                else -> result.append(char)
            }
        }
    }

    private fun arrayValue(): List<Any?> {
        at++
        val items = ArrayList<Any?>()
        space()
        if (at < text.length && text[at] == ']') {
            at++
            return items
        }
        while (true) {
            items += value()
            space()
            if (at >= text.length) fail()
            when (text[at++]) {
                ',' -> Unit
                ']' -> return items
                else -> fail()
            }
        }
    }

    private fun objectValue(): Map<String, Any?> {
        at++
        val fields = HashMap<String, Any?>()
        space()
        if (at < text.length && text[at] == '}') {
            at++
            return fields
        }
        while (true) {
            space()
            if (at >= text.length || text[at] != '"') fail()
            val name = stringValue()
            space()
            if (at >= text.length || text[at++] != ':') fail()
            fields[name] = value()
            space()
            if (at >= text.length) fail()
            when (text[at++]) {
                ',' -> Unit
                '}' -> return fields
                else -> fail()
            }
        }
    }
}
