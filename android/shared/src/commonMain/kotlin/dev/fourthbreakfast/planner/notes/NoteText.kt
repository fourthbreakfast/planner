package dev.fourthbreakfast.planner.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.fourthbreakfast.planner.core.BOLD
import dev.fourthbreakfast.planner.core.ITALIC
import dev.fourthbreakfast.planner.core.LineKind
import dev.fourthbreakfast.planner.core.NoteLine
import dev.fourthbreakfast.planner.core.UNDERLINE
import dev.fourthbreakfast.planner.core.numberLabels
import dev.fourthbreakfast.planner.ui.CheckBoxMark
import dev.fourthbreakfast.planner.ui.theme.Space

/** How a look (bold, italic, underline bits) is drawn. */
fun spanFor(look: Int): SpanStyle = SpanStyle(
    fontWeight = if (look and BOLD != 0) FontWeight.Bold else null,
    fontStyle = if (look and ITALIC != 0) FontStyle.Italic else null,
    textDecoration = if (look and UNDERLINE != 0) TextDecoration.Underline else null,
)

/** Appends [text], each run of letters with the same look in [looks] as one styled stretch. */
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
    LineKind.CHECK -> null
}

/** A note as text for the list: the first lines, marked and pushed in as they are, with their looks. */
fun previewOf(lines: List<NoteLine>, maxLines: Int): AnnotatedString {
    val numbers = numberLabels(lines)
    val trimmed = lines.dropLastWhile { it.text.isEmpty() && it.kind == LineKind.PLAIN }.ifEmpty { lines.take(1) }
    return buildAnnotatedString {
        trimmed.take(maxLines).forEachIndexed { index, line ->
            if (index > 0) append('\n')
            append("  ".repeat(line.indent))
            if (line.kind == LineKind.CHECK) {
                appendInlineContent(if (line.checked) CHECKED else UNCHECKED, if (line.checked) "[x]" else "[ ]")
                append(' ')
            }
            markerFor(line, numbers[index])?.let { append("$it ") }
            appendStyled(line.text, line.styles)
        }
        if (trimmed.size > maxLines) append("\n…")
    }
}

// every line the same height, whether it wraps or not
private val EVEN_LINES = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private const val CHECKED = "checked"
private const val UNCHECKED = "unchecked"

/** The drawn check boxes a preview from [previewOf] puts inline. */
@Composable
fun rememberPreviewBoxes(ink: Color): Map<String, InlineTextContent> = remember(ink) {
    fun box(checked: Boolean) = InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
        CheckBoxMark(checked, ink, Modifier.fillMaxSize())
    }
    mapOf(CHECKED to box(true), UNCHECKED to box(false))
}

/** The whole body as it shows in the editor: each line pushed in, its letters with their looks, checked lines quiet. */
internal fun styledBody(shown: String, marks: List<LineMark>, looks: List<Int>, quiet: Color, density: Density): AnnotatedString = buildAnnotatedString {
    // each line is a paragraph, which breaks by itself: a line break left in would make a second one. something of the same
    // length stands in for it, so places in this text still match places in the real one
    append(shown.replace('\n', GUARD))
    val text = shown.drop(1)
    if (looks.size != text.length) return@buildAnnotatedString
    looks.forEachIndexed { i, look -> if (look != 0 && text[i] != '\n') addStyle(spanFor(look), i + 1, i + 2) }
    var start = 0
    text.split('\n').forEachIndexed { index, words ->
        val mark = marks.getOrElse(index) { LineMark() }
        val first = if (index == 0) 0 else start + 1
        val end = minOf(start + words.length + 2, shown.length)
        val push = with(density) { (Space.indent * mark.indent + if (mark.kind == LineKind.PLAIN) 0.dp else Space.markWidth).toSp() }
        addStyle(ParagraphStyle(textIndent = TextIndent(push, push), lineHeightStyle = EVEN_LINES), first, end)
        if (mark.checked) addStyle(SpanStyle(color = quiet), first, end)
        start += words.length + 1
    }
}
