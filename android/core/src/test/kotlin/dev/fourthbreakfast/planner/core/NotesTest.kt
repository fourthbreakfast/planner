package dev.fourthbreakfast.planner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryNoteStore : NoteStore {
    val state = MutableStateFlow<List<Note>>(emptyList())

    override fun observeAll(): Flow<List<Note>> = state

    override suspend fun all() = state.value

    override suspend fun insert(notes: List<Note>) {
        state.value = state.value + notes
    }

    override suspend fun update(notes: List<Note>) {
        val changed = notes.associateBy { it.id }
        state.value = state.value.map { changed[it.id] ?: it }
    }

    override suspend fun delete(notes: List<Note>) {
        val gone = notes.mapTo(HashSet()) { it.id }
        state.value = state.value.filterNot { it.id in gone }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

class NotesTest {
    private val store = MemoryNoteStore()
    private var now = 100L
    private val notes = Notes(store) { now++ }

    private fun order() = sortedNotes(store.state.value.filterNot { it.inBin }).map { it.id }

    @Test
    fun newNotesGoLast() = runBlocking {
        val a = notes.add()
        val b = notes.add()
        val c = notes.add()
        assertEquals(listOf(a, b, c), order())
    }

    @Test
    fun movingPutsANoteBeforeAnotherOrLast() = runBlocking {
        val a = notes.add()
        val b = notes.add()
        val c = notes.add()
        notes.move(c, beforeId = a)
        assertEquals(listOf(c, a, b), order())
        notes.move(c, beforeId = null)
        assertEquals(listOf(a, b, c), order())
        notes.move(a, beforeId = a)
        assertEquals(listOf(a, b, c), order())
    }

    @Test
    fun editingChangesTheBodyAndWhenItChanged() = runBlocking {
        val id = notes.add()
        val body = formatBody(listOf(NoteLine(text = "hello", styles = listOf(0, 0, 0, 0, 0))))
        notes.update(id, "", body)
        val note = store.state.value.single()
        assertEquals(body, note.body)
        assertTrue(note.updatedAt > note.createdAt)
    }

    @Test
    fun binRestoreAndDeleteForever() = runBlocking {
        val a = notes.add()
        val b = notes.add()
        notes.moveToBin(a)
        assertEquals(listOf(b), order())
        notes.restore(a)
        assertEquals(listOf(a, b), order())
        notes.moveToBin(a)
        notes.deleteForever(b)
        assertEquals(2, store.state.value.size)
        notes.deleteForever(a)
        assertEquals(listOf(b), store.state.value.map { it.id })
        notes.moveToBin(b)
        notes.emptyBin()
        assertTrue(store.state.value.isEmpty())
    }

    @Test
    fun aNoteLeftEmptyIsDroppedButOneWithWordsStays() = runBlocking {
        val empty = notes.add()
        val written = notes.add()
        notes.update(written, "", formatBody(listOf(NoteLine(text = "x", styles = listOf(0)))))
        notes.dropIfEmpty(empty)
        notes.dropIfEmpty(written)
        assertEquals(listOf(written), order())
    }
}

class NoteBodyTest {
    @Test
    fun aBodyReadsBackExactlyAsWritten() {
        val lines = listOf(
            NoteLine(LineKind.PLAIN, 0, false, "plain", List(5) { 0 }),
            NoteLine(LineKind.BULLET, 2, false, "bold it", listOf(BOLD, BOLD or ITALIC, ITALIC, 0, UNDERLINE, UNDERLINE, UNDERLINE)),
            NoteLine(LineKind.CHECK, 0, true, "done", List(4) { 0 }),
            NoteLine(),
        )
        assertEquals(lines, parseBody(formatBody(lines)))
    }

    @Test
    fun anEmptyBodyIsOneEmptyLine() {
        assertEquals(listOf(NoteLine()), parseBody(""))
    }

    @Test
    fun cleaningRemovesLineBreaksAndMarkers() {
        assertEquals("ab", cleanLineText("a\nb\r"))
    }

    @Test
    fun numbersCountPerDepthAndRestartAfterOtherLines() {
        val lines = listOf(
            NoteLine(LineKind.NUMBER, 0),
            NoteLine(LineKind.NUMBER, 1),
            NoteLine(LineKind.NUMBER, 1),
            NoteLine(LineKind.NUMBER, 0),
            NoteLine(LineKind.PLAIN, 0),
            NoteLine(LineKind.NUMBER, 0),
        )
        assertEquals(listOf(1, 1, 2, 2, null, 1), numberLabels(lines))
    }

    @Test
    fun blankBodiesAreBlank() {
        assertTrue(isBlankBody(formatBody(listOf(NoteLine(), NoteLine(LineKind.BULLET)))))
        assertNull(numberLabels(listOf(NoteLine())).single())
    }
}

class NoteStyleTest {
    @Test
    fun typingInTheMiddleKeepsTheRestAndStylesTheNewLetters() {
        val result = restyle("abcd", listOf(BOLD, BOLD, 0, 0), "abXcd", ITALIC)
        assertEquals(listOf(BOLD, BOLD, ITALIC, 0, 0), result)
    }

    @Test
    fun deletingKeepsTheLooksOfWhatIsLeft() {
        assertEquals(listOf(BOLD, 0), restyle("abc", listOf(BOLD, ITALIC, 0), "ac", 0))
        assertEquals(emptyList<Int>(), restyle("a", listOf(BOLD), "", 0))
    }

    @Test
    fun replacingAWordRestylesTheWholeStretch() {
        assertEquals(listOf(0, 0, UNDERLINE, UNDERLINE), restyle("a-b", listOf(0, 0, 0), "a-xy", UNDERLINE))
    }

    @Test
    fun togglingSwitchesOffOnlyWhenEverythingHasIt() {
        assertEquals(listOf(BOLD, BOLD, 0), toggleStyle(listOf(BOLD, 0, 0), 0, 2, BOLD))
        assertEquals(listOf(0, 0, 0), toggleStyle(listOf(BOLD, BOLD, 0), 0, 2, BOLD))
        assertEquals(listOf(BOLD or ITALIC, ITALIC), toggleStyle(listOf(BOLD, 0), 0, 2, ITALIC))
        assertEquals(listOf(0, 0), toggleStyle(listOf(0, 0), 1, 1, BOLD))
    }
}

class NoteTitleTest {
    private fun body(vararg words: String) = formatBody(words.map { NoteLine(text = it, styles = List(it.length) { 0 }) })

    @Test
    fun anEmptyTitleBorrowsTheFirstLineAndTheListSkipsIt() {
        val note = Note(body = body("", " milk ", "eggs"), createdAt = 1)
        assertEquals("milk", noteTitle(note))
        assertEquals(listOf("eggs"), linesUnderTitle(note).map { it.text })
    }

    @Test
    fun aTypedTitleWinsAndKeepsEveryLine() {
        val note = Note(title = " shopping ", body = body("milk", "eggs"), createdAt = 1)
        assertEquals("shopping", noteTitle(note))
        assertEquals(2, linesUnderTitle(note).size)
    }

    @Test
    fun aNoteWithOnlyATitleIsNotEmpty() = runBlocking {
        val store = MemoryNoteStore()
        val notes = Notes(store) { 1L }
        val id = notes.add()
        notes.update(id, "idea", formatBody(listOf(NoteLine())))
        notes.dropIfEmpty(id)
        assertEquals(1, store.state.value.size)
    }

    @Test
    fun sharedTextBecomesALinePerLine() = runBlocking {
        val store = MemoryNoteStore()
        val notes = Notes(store) { 1L }
        notes.add(" a link ", plainBody("first\r\nsecond\n\nfourth"))
        val note = store.state.value.single()
        assertEquals("a link", note.title)
        assertEquals(listOf("first", "second", "", "fourth"), parseBody(note.body).map { it.text })
    }

    @Test
    fun pinnedNotesStayAboveTheOthersAndKeepTheirOwnOrder() = runBlocking {
        val store = MemoryNoteStore()
        val notes = Notes(store) { 1L }
        val a = notes.add()
        val b = notes.add()
        val c = notes.add()
        notes.setPinned(c, true)
        assertEquals(listOf(c, a, b), sortedNotes(store.state.value).map { it.id })
        notes.setPinned(b, true)
        assertEquals(listOf(b, c, a), sortedNotes(store.state.value).map { it.id })
        notes.setPinned(c, false)
        assertEquals(listOf(b, a, c), sortedNotes(store.state.value).map { it.id })
    }

    @Test
    fun aSearchNeedsEveryWordAndIgnoresCase() {
        val words = searchWords("  Milk  EGGS ")
        assertEquals(listOf("milk", "eggs"), words)
        assertTrue(matchesAll("buy eggs and Milk", words))
        assertTrue(!matchesAll("buy eggs", words))
        assertTrue(!matchesAll("anything", emptyList()))
    }

    @Test
    fun aNoteIsSearchedByTitleAndBody() {
        val note = Note(title = "shopping", body = formatBody(listOf(NoteLine(text = "oat milk", styles = List(8) { 0 }))), createdAt = 1)
        assertTrue(matchesAll(noteText(note), searchWords("shop milk")))
        assertTrue(!matchesAll(noteText(note), searchWords("bread")))
    }

    @Test
    fun theWidgetListsTodaysEventsThenOpenTasksAndCountsWhatIsLeftOut() {
        val events = listOf(
            Event(title = "later", day = 5, minute = 600, createdAt = 1),
            Event(title = "holiday", day = 5, createdAt = 2),
            Event(title = "tomorrow", day = 6, minute = 60, createdAt = 3),
            Event(title = "binned", day = 5, minute = 30, createdAt = 4, deletedAt = 9),
        )
        val tasks = listOf(
            Task(title = "plain", position = 1.0, createdAt = 1),
            Task(title = "urgent", position = 2.0, priority = Priority.HIGH, createdAt = 2),
            Task(title = "done", position = 3.0, done = true, createdAt = 3),
            Task(title = "kid", parentId = "x", position = 1.0, createdAt = 4),
        )
        val all = widgetLines(events, tasks, 5, 10) { "%02d:%02d".format(it / 60, it % 60) }
        assertEquals(listOf("all day  holiday", "10:00    later", "·        urgent", "·        plain"), all)
        val short = widgetLines(events, tasks, 5, 3) { "%02d:%02d".format(it / 60, it % 60) }
        assertEquals(listOf("all day  holiday", "10:00    later", "+2 more"), short)
    }
}
