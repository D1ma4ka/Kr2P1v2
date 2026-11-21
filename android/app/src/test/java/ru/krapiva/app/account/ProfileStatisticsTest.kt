package ru.krapiva.app.account

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileStatisticsTest {
    @Test fun groupsCoverEveryQuarterExactlyOnce() {
        val histogram = (1..20).associateWith { it }
        val groups = groupRatings(histogram)
        assertEquals(listOf(10, 26, 42, 58, 74), groups.map { it.count })
        assertEquals(histogram.values.sum(), groups.sumOf { it.count })
        assertEquals(20, groups.last().last)
    }

    @Test fun previewCombinesSmallStylesWithoutChangingTheTotal() {
        val styles = (1..8).map { StyleShare("Style $it", it) }
        val preview = compactStyleShares(styles)
        assertEquals(listOf(8, 7, 6, 5, 4, 6), preview.map { it.count })
        assertEquals(styles.sumOf { it.count }, preview.sumOf { it.count })
    }
}
