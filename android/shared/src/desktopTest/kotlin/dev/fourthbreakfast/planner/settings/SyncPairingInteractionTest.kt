package dev.fourthbreakfast.planner.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.sync.SyncControls
import dev.fourthbreakfast.planner.sync.SyncState
import dev.fourthbreakfast.planner.ui.BackStack
import dev.fourthbreakfast.planner.ui.LocalBackStack
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPairingInteractionTest {
    private val fullId = "ABCDEFG" + "A".repeat(45)

    private class Controller : SyncControls {
        override val state = MutableStateFlow(SyncState(myId = "Z".repeat(52), running = true))
        override val available = true
        override val anywhere = true
        override val deviceName = "test device"
        val requests = mutableListOf<String>()
        override fun renameThisDevice(name: String) {}
        override fun openPairing() {}
        override fun closePairing() {}
        override fun chooseAnywhere(on: Boolean) {}
        override suspend fun pairWith(code: String): Boolean {
            requests += code
            return code.substringBefore('?').replace("-", "").length >= 52
        }
        override suspend fun syncNow() {}
        override fun letIn(id: String) {}
        override fun refuse(id: String) {}
        override fun unpair(id: String) {}
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
    private suspend fun ImageComposeScene.settle() = repeat(12) {
        render(System.nanoTime())
        delay(25)
    }
    private suspend fun ImageComposeScene.typeCode(text: String) {
        val field = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("type a code") == true }
        assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text)))
        settle()
    }
    private suspend fun ImageComposeScene.click(label: String) {
        val button = nodes().first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
    }
    private fun withScreen(test: suspend (ImageComposeScene, Controller) -> Unit) = runBlocking(Dispatchers.Main) {
        val controller = Controller()
        ImageComposeScene(700, 1000, Density(1f)) {
            CompositionLocalProvider(LocalBackStack provides BackStack()) {
                PlannerTheme { SyncScreen(controller, scan = null, failed = "sync failed", onClose = {}) }
            }
        }.use { scene ->
            scene.settle()
            test(scene, controller)
        }
    }

    @Test
    fun pastedFullCodeKeepsItsAddressMetadata() = withScreen { scene, controller ->
        val code = fullId + "?addr=tcp://192.168.1.4:22000"
        scene.typeCode(code)
        scene.click("pair")
        assertEquals(listOf(code), controller.requests)
    }

    @Test
    fun discoveryRetriesOnlyAfterTheUserExplicitlyRequestedPairing() = withScreen { scene, controller ->
        scene.typeCode("abcdefg")
        controller.state.value = controller.state.value.copy(nearby = listOf(fullId))
        scene.settle()
        assertTrue(controller.requests.isEmpty())
        controller.state.value = controller.state.value.copy(nearby = emptyList())
        scene.click("pair")
        assertEquals(listOf("abcdefg"), controller.requests)
        controller.state.value = controller.state.value.copy(nearby = listOf(fullId))
        scene.settle()
        assertEquals(listOf("abcdefg", fullId), controller.requests)
    }

    @Test
    fun editingTheCodeCancelsAnEarlierFailedRequest() = withScreen { scene, controller ->
        scene.typeCode("abcdefg")
        scene.click("pair")
        scene.typeCode("another")
        controller.state.value = controller.state.value.copy(nearby = listOf(fullId))
        scene.settle()
        assertEquals(listOf("abcdefg"), controller.requests)
    }

    @Test
    fun theFirstMouseClickSelectsTheWholeDeviceNameAfterRelease() = withScreen { scene, _ ->
        fun field() = scene.nodes().first { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "test device" }
        suspend fun clickName() {
            val position = field().boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, position, type = PointerType.Mouse, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, position, type = PointerType.Mouse, buttons = PointerButtons())
            scene.settle()
        }
        clickName()
        assertEquals(TextRange(0, "test device".length), field().config[SemanticsProperties.TextSelectionRange])
        clickName()
        assertTrue("A subsequent click should position the caret", field().config[SemanticsProperties.TextSelectionRange].collapsed)
    }
}
