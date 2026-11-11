package dev.fourthbreakfast.planner.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertFalse
import org.junit.Test

class LigaturesTest {
    @Test
    fun arrowSequencesAreShapedByTheFont() {
        fun rendered(features: String): ByteArray = ImageComposeScene(600, 100, Density(1f)) {
            PlannerTheme(darkTheme = false) {
                BasicText(
                    "a -> b => c != d === e",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = dev.fourthbreakfast.planner.ui.theme.firaCode(),
                        fontSize = 28.sp,
                        fontFeatureSettings = features,
                    ),
                )
            }
        }.use { scene ->
            repeat(15) {
                scene.render(it * 100_000_000L)
                Thread.sleep(20)
            }
            scene.render(2_000_000_000L).encodeToData()!!.bytes
        }
        assertFalse("The bundled font must draw programming ligatures", rendered("calt, liga").contentEquals(rendered("calt 0, liga 0")))
    }
}
