package dev.fourthbreakfast.planner.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGeometryTest {
    @Test
    fun shortTaskDocumentKeepsNavigationBelowTheLogo() {
        val top = navigationGroupTop(56f, 160f, 240f, 800f)
        assertTrue(top >= 56f)
    }

    @Test
    fun navigationCentersBetweenContentDividers() {
        val top = navigationGroupTop(56f, 560f, 240f, 800f)
        assertEquals(308f, top + 120f, 0.01f)
    }

    @Test
    fun longContentDoesNotPushNavigationIntoFooter() {
        val top = navigationGroupTop(56f, 1800f, 240f, 600f)
        assertTrue(top + 240f <= 600f - 72f)
    }

    @Test
    fun veryShortWindowDoesNotProduceInvalidClampBounds() {
        assertEquals(0f, navigationGroupTop(56f, 90f, 240f, 180f), 0.01f)
    }
}
