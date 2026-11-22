package ru.krapiva.app.menu

import org.junit.Assert.*
import org.junit.Test

class MenuComparisonTest {
    private val can = testPosition("can", "IPA", packaging = "can").copy(name = "IPA")
    private val bottle = testPosition("bottle", "IPA", packaging = "bottle", abv = 6.0)
        .copy(name = "IPA", personalCheckins = 0)
    private val menu = listOf(can, bottle, testPosition("third"), testPosition("fourth"), testPosition("fifth"))

    @Test fun selectsTwoThreeFourAndRejectsFifthAndForeignPositions() {
        var ids = emptyList<String>()
        for (item in menu.take(4)) {
            ids = toggleComparedPosition(ids, item.positionId, menu)
            assertEquals(ids, comparisonPositions(ids, menu).map(MenuPosition::positionId))
        }
        assertEquals(4, ids.size)
        assertEquals(ids, toggleComparedPosition(ids, "fifth", menu))
        assertEquals(ids, toggleComparedPosition(ids, "another-bar-only", menu))
        assertEquals(listOf("can", "third", "fourth"), toggleComparedPosition(ids, "bottle", menu))
        assertTrue(comparisonPositions(ids, listOf(testPosition("other"))).isEmpty())
        assertEquals(listOf(can), comparisonPositions(listOf("can", "can", "bottle"), listOf(can)))
    }

    @Test fun sameNamePackagesAndUnknownValuesStayDistinct() {
        val selected = comparisonPositions(listOf("can", "bottle"), menu)
        val fields = comparisonFields(selected).associateBy(ComparisonField::label)
        assertEquals(2, selected.size)
        assertEquals(listOf("Банка", "Бутылка"), fields.getValue("Упаковка и подача").values)
        assertTrue(fields.getValue("Упаковка и подача").differs)
        assertEquals(listOf(null, "6.0%"), fields.getValue("Крепость · ABV").values)
        assertEquals(listOf(null, null), fields.getValue("Горечь · IBU").values)
        assertEquals(listOf(null, null), fields.getValue("Средний рейтинг").values)
        assertEquals(listOf(null, "0"), fields.getValue("Мои чекины").values)
    }
}
