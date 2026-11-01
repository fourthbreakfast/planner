package dev.fourthbreakfast.planner.core

/** The words of a search: lowercase, split on spaces. A note or task matches when it has every one of them. */
fun searchWords(query: String): List<String> = query.lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

fun matchesAll(text: String, words: List<String>): Boolean {
    val lower = text.lowercase()
    return words.isNotEmpty() && words.all { it in lower }
}

/** Everything a search looks through in an event. */
fun eventText(event: Event): String = listOf(event.title, event.location, event.notes).joinToString("\n")

/** Everything a search looks through in a note: its title and the words of every line. */
fun noteText(note: Note): String = note.title + "\n" + parseBody(note.body).joinToString("\n") { it.text }
