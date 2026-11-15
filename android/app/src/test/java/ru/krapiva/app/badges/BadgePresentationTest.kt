package ru.krapiva.app.badges
import org.junit.Assert.*
import org.junit.Test
class BadgePresentationTest {
    @Test fun serverMetricLabelsAndProgressAreBoundedWithoutGrantingAwards() {
        assertEquals("Стили",badgeCategory("styles"))
        assertEquals("Другие достижения",badgeCategory("future-rule"))
        assertEquals(0f,badgeFraction(3,0),0f)
        assertEquals(1f,badgeFraction(50,3),0f)
        assertEquals(0f,badgeFraction(-1,3),0f)
    }
}
