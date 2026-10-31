package dev.fourthbreakfast.planner.core

enum class LineKind(val code: Char) {
    PLAIN('p'),
    BULLET('b'),
    NUMBER('n'),
    CHECK('c'),
}

const val BOLD = 1
const val ITALIC = 2
const val UNDERLINE = 4

/** How far a line can be pushed in. */
const val MAX_INDENT = 5

// the three look markers, in a range nobody types: each switches its look on or off from there on
private const val BOLD_MARK = ''
private const val ITALIC_MARK = ''
private const val UNDERLINE_MARK = ''

/**
 * One line of a note: its text, how it is marked (plain, bullet, number or checkbox), how far it is pushed in, and
 * for every character whether it is bold, italic or underlined, as bits of [BOLD], [ITALIC] and [UNDERLINE].
 */
data class NoteLine(
    val kind: LineKind = LineKind.PLAIN,
    val indent: Int = 0,
    val checked: Boolean = false,
    val text: String = "",
    val styles: List<Int> = List(text.length) { 0 },
) {
    init {
        require(styles.size == text.length) { "one look for every character" }
    }
}

/** Text made safe for a line: no line breaks and none of the markers the format keeps for itself. */
fun cleanLineText(text: String): String = text.filterNot { it == '\n' || it == '\r' || it in BOLD_MARK..UNDERLINE_MARK }

/**
 * A note as text, one line to a line: a mark, an indent, a checked flag, a space, then the words with the look
 * markers switched on and off between characters. Readable enough to repair by hand, and nothing can leak out of a line.
 */
fun formatBody(lines: List<NoteLine>): String = lines.joinToString("\n") { line ->
    buildString {
        append(line.kind.code)
        append(line.indent.coerceIn(0, MAX_INDENT))
        append(if (line.checked) 'y' else 'n')
        append(' ')
        var current = 0
        line.text.forEachIndexed { index, char ->
            val next = line.styles[index]
            if (next and BOLD != current and BOLD) append(BOLD_MARK)
            if (next and ITALIC != current and ITALIC) append(ITALIC_MARK)
            if (next and UNDERLINE != current and UNDERLINE) append(UNDERLINE_MARK)
            current = next
            append(char)
        }
    }
}

/** The lines of a note. A body with nothing in it is one empty line, never no lines. */
fun parseBody(body: String): List<NoteLine> {
    if (body.isEmpty()) return listOf(NoteLine())
    return body.split('\n').map { raw ->
        val kind = LineKind.entries.firstOrNull { raw.getOrNull(0) == it.code } ?: LineKind.PLAIN
        val indent = (raw.getOrNull(1)?.digitToIntOrNull() ?: 0).coerceIn(0, MAX_INDENT)
        val checked = raw.getOrNull(2) == 'y'
        val words = if (raw.length > 4) raw.substring(4) else ""
        val text = StringBuilder()
        val styles = ArrayList<Int>()
        var current = 0
        words.forEach { char ->
            when (char) {
                BOLD_MARK -> current = current xor BOLD
                ITALIC_MARK -> current = current xor ITALIC
                UNDERLINE_MARK -> current = current xor UNDERLINE
                else -> {
                    text.append(char)
                    styles += current
                }
            }
        }
        NoteLine(kind, indent, checked, text.toString(), styles)
    }
}

/** The number to show beside each line, or null for lines that are not numbered. Each depth counts for itself. */
fun numberLabels(lines: List<NoteLine>): List<Int?> {
    val counters = IntArray(MAX_INDENT + 1)
    return lines.map { line ->
        for (deeper in line.indent + 1..MAX_INDENT) counters[deeper] = 0
        if (line.kind == LineKind.NUMBER) {
            counters[line.indent]++
            counters[line.indent]
        } else {
            counters[line.indent] = 0
            null
        }
    }
}

/** True when nothing has been written: only empty lines. */
fun isBlankBody(body: String): Boolean = parseBody(body).all { it.text.isBlank() }

/**
 * The looks of a line after its text changed from [old] to [new]: characters that stayed keep theirs, characters
 * that came in get [typing]. The change is taken as one stretch replaced in the middle, which is what typing,
 * deleting and pasting all are.
 */
fun restyle(old: String, oldStyles: List<Int>, new: String, typing: Int): List<Int> {
    var head = 0
    while (head < old.length && head < new.length && old[head] == new[head]) head++
    var tail = 0
    while (tail < old.length - head && tail < new.length - head && old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++
    return oldStyles.subList(0, head) + List(new.length - head - tail) { typing } + oldStyles.subList(old.length - tail, old.length)
}

/** Switches [bit] over a range of looks: off when every character in it already has it, on otherwise. */
fun toggleStyle(styles: List<Int>, from: Int, to: Int, bit: Int): List<Int> {
    val start = from.coerceIn(0, styles.size)
    val end = to.coerceIn(start, styles.size)
    if (start == end) return styles
    val allHave = (start until end).all { styles[it] and bit != 0 }
    return styles.mapIndexed { i, look ->
        if (i !in start until end) look else if (allHave) look and bit.inv() else look or bit
    }
}
