package dev.fourthbreakfast.planner.notes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.numberLabels

/** How a look (bold, italic, underline bits) is drawn. */
fun spanFor(look: Int): SpanStyle = SpanStyle(
    fontWeight = if (look and BOLD != 0) FontWeight.Bold else null,
    fontStyle = if (look and ITALIC != 0) FontStyle.Italic else null,
    textDecoration = if (look and UNDERLINE != 0) TextDecoration.Underline else null,
)

/** The words of a line with their looks, starting [offset] characters in (the editor keeps a guard in front). */
fun AnnotatedString.Builder.appendStyled(text: String, looks: List<Int>) {
    var start = 0
    while (start < text.length) {
        var end = start + 1
        while (end < text.length && looks[end] == looks[start]) end++
        if (looks[start] == 0) append(text.substring(start, end)) else withStyle(spanFor(looks[start])) { append(text.substring(start, end)) }
        start = end
    }
}

/** What stands before a line's words: a dot, a number or a box. */
fun markerFor(line: NoteLine, number: Int?): String? = when (line.kind) {
    LineKind.PLAIN -> null
    LineKind.BULLET -> "•"
    LineKind.NUMBER -> "${number ?: 1}."
    LineKind.CHECK -> if (line.checked) "[x]" else "[ ]"
}

/** A note as text for the list: the first lines, marked and pushed in as they are, with their looks. */
fun previewOf(lines: List<NoteLine>, maxLines: Int): AnnotatedString {
    val numbers = numberLabels(lines)
    val trimmed = lines.dropLastWhile { it.text.isEmpty() && it.kind == LineKind.PLAIN }.ifEmpty { lines.take(1) }
    return buildAnnotatedString {
        trimmed.take(maxLines).forEachIndexed { index, line ->
            if (index > 0) append('\n')
            append("  ".repeat(line.indent))
            markerFor(line, numbers[index])?.let { append("$it ") }
            appendStyled(line.text, line.styles)
        }
        if (trimmed.size > maxLines) append("\n…")
    }
}
