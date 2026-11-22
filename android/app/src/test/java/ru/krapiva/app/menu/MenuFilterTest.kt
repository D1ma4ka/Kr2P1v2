package ru.krapiva.app.menu

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

internal fun testPosition(
    id: String, style: String? = null, brewery: String = "one", abv: Double? = null,
    ibu: Double? = null, tried: Boolean = false, packaging: String? = null, price: String? = null,
    currency: String? = null, rating: Double? = null, country: String? = null, available: Boolean = true,
) = MenuPosition(id, "test", "Test $id", brewery, brewery, style?.let { "style-$it" }, style, abv, ibu,
    rating, country, packaging, null, price?.let(::BigDecimal), currency, null, available, tried, publicAverageRating = rating)

class MenuFilterTest {
    @Test fun styleAliasesAndFamilies() {
        val names = listOf("IPA", "APA", "Мутный IPA", "IPA - Imperial / Double", "Session IPA", "Cold IPA", "IPA - American")
        val rows = names.mapIndexed { index, name -> testPosition(index.toString(), name) } +
            testPosition("lager", "Hoppy IPA-inspired lager")
        assertEquals(names.size, filterMenu(rows, MenuFilter(families = setOf(MenuStyles.HOPPY))).size)
        assertEquals(listOf("1", "2"), filterMenu(rows, MenuFilter(styles = setOf("apa", "neipa"))).map { it.positionId })
        assertTrue(MenuStyles.classify("New England IPA")!!.matchesSearch("мутный"))
        assertNull(MenuStyles.classify("Hoppy IPA-inspired lager"))
    }

    @Test fun combinationsUseFullMenuAndKeepPackagesSeparate() {
        val rows = (1..50).map { testPosition("filler-$it", "Porter") } + listOf(
            testPosition("can", "APA", brewery = "one", abv = 4.0, ibu = 0.0, tried = true, packaging = "can"),
            testPosition("bottle", "APA", brewery = "one", abv = 4.0, ibu = 0.0, packaging = "bottle"),
            testPosition("tap", "NEIPA", brewery = "two", abv = 8.0, ibu = 30.0),
            testPosition("unknown", "NEIPA", brewery = "two", ibu = 30.0),
            testPosition("other", "NEIPA", brewery = "three", abv = 8.0, ibu = 30.0),
        )
        val filter = MenuFilter(styles = setOf("apa", "neipa"), breweries = setOf("one", "two"),
            abv = MenuRange("4", "8"), ibu = MenuRange("0", "30"), tried = Tried.NO)
        assertEquals(listOf("bottle", "tap"), filterMenu(rows, filter).map { it.positionId })
        assertEquals(listOf("can"), filterMenu(rows, filter.copy(tried = Tried.YES)).map { it.positionId })
        assertEquals(rows, filterMenu(rows, MenuFilter()))
        assertFalse(MenuFilter(abv = MenuRange("8", "4")).valid())
    }

    @Test fun nullValuesSortLastAndPricesNeverCrossCurrencies() {
        val rows = listOf(
            testPosition("null"),
            testPosition("b", abv = 4.0, ibu = 20.0, price = "4", currency = "EUR", rating = 3.0, country = "Франция", available = false),
            testPosition("a", abv = 4.0, ibu = 2.0, price = "4", currency = "EUR", rating = 4.0, country = "Франция"),
            testPosition("x", abv = 8.0, ibu = 10.0, price = "4", currency = "USD"),
        )
        fun ids(filter: MenuFilter) = filterMenu(rows, filter).map { it.positionId }
        assertEquals(listOf("x", "a", "b", "null"), ids(MenuFilter(sort = MenuSort.ABV_DESC)))
        assertEquals(listOf("a", "x", "b", "null"), ids(MenuFilter(sort = MenuSort.IBU_ASC)))
        assertEquals(listOf("a", "b", "x", "null"), ids(MenuFilter(sort = MenuSort.PRICE)))
        assertEquals(listOf("a", "b", "null", "x"), ids(MenuFilter(sort = MenuSort.RELEVANCE)))
        assertEquals(listOf("a"), ids(MenuFilter(countries = setOf(normalized("Франция")), ratingMin = "4",
            price = MenuRange("4", "4"), currency = "EUR", available = true)))
        assertEquals(listOf("x"), ids(MenuFilter(query = "ＴＥＳＴ X")))
    }
}
