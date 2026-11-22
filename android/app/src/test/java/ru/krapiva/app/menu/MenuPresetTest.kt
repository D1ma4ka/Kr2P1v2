package ru.krapiva.app.menu

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MenuPresetTest {
    private val now = Instant.parse("2026-11-22T10:00:00Z")

    @Test fun fiveThemesAndRatingFallback() {
        val rows = listOf(
            testPosition("tripel", "Tripel", tried = true),
            testPosition("ipa", "Session IPA", rating = 3.0),
            testPosition("apa", "APA", rating = 4.0),
            testPosition("stout", "Стаут"),
            testPosition("gose", "Gose", rating = 2.0),
        )
        val expected = listOf(listOf("tripel"), listOf("apa", "ipa"), listOf("stout"), listOf("gose"),
            listOf("apa", "ipa", "gose", "stout"))
        val results = themePresets.map { theme -> filterMenu(rows, theme.filter).map { it.positionId } }
        assertEquals(expected, results)
        val hunt = themePresets.last().filter
        assertEquals("gose", filterMenu(rows, hunt, mapOf("gose" to 10)).first().positionId)
    }

    @Test fun savedRuleChangesResultsAcrossBarsAndSupportsLifecycle() {
        val ipa = themePresets[1].filter.copy(tried = Tried.NO, abv = MenuRange("4", "8"))
        val created = MenuPresetBook().save("own", " Мои IPA ", ipa, now).selectDefault("own").use("own")
        val renamed = created.rename("own", "Хмель", now).duplicate("own", "copy", "Вторая версия", now)
        val edited = renamed.update("copy", ipa.copy(tried = Tried.YES), now).use("copy").use("own")
        assertEquals(listOf("own", "copy"), edited.recentIds)
        assertEquals("Хмель", edited.title("own"))
        assertEquals("own", edited.defaultId)
        assertEquals(ipa, edited.filter("own"))
        assertEquals(Tried.YES, edited.filter("copy")?.tried)
        val first = listOf(testPosition("first", "NEIPA", abv = 6.0))
        val second = listOf(testPosition("second", "APA", abv = 5.0), testPosition("unknown", "APA"))
        assertEquals("first", filterMenu(first, ipa).single().positionId)
        assertEquals("second", filterMenu(second, ipa).single().positionId)
        assertTrue(filterMenu(second, edited.filter("copy")!!).isEmpty())
        val removed = edited.delete("own", now)
        assertNull(removed.defaultId)
        assertNull(removed.filter("own"))
        assertEquals(listOf("copy"), removed.recentIds)
        assertTrue(removed.records.first { it.id == "own" }.deleted)
        assertEquals(Tried.ALL, themePresets[1].filter.tried)
    }

    @Test fun menuFreshnessBoundary() {
        val menu = BarMenu(now, "test", emptyMap(), emptyList())
        assertFalse(menu.isOld(now.plusSeconds(86_399)))
        assertTrue(menu.isOld(now.plusSeconds(86_400)))
    }
}
