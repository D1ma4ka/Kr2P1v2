package ru.krapiva.app.menu

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal enum class Tried(val title: String) {
    ALL("Все"), YES("Пробовал"), NO("Не пробовал"),
}

internal enum class MenuSort(val title: String) {
    MENU("В порядке меню"), NAME("По названию"), STYLE("По стилю"), BREWERY("По пивоварне"),
    ABV_ASC("ABV ↑"), ABV_DESC("ABV ↓"), IBU_ASC("IBU ↑"), IBU_DESC("IBU ↓"),
    RATING("По рейтингу"), PRICE("По цене"), NEW_FIRST("Сначала непробованные"),
    RELEVANCE("По персональной релевантности"),
}

internal data class MenuRange(val min: String = "", val max: String = "") {
    val active get() = min.isNotBlank() || max.isNotBlank()
    val lower get() = min.replace(',', '.').toBigDecimalOrNull()
    val upper get() = max.replace(',', '.').toBigDecimalOrNull()
    fun valid(limit: BigDecimal): Boolean {
        val low = lower
        val high = upper
        return (min.isBlank() || low != null && low in BigDecimal.ZERO..limit) &&
            (max.isBlank() || high != null && high in BigDecimal.ZERO..limit) &&
            (low == null || high == null || low <= high)
    }
    fun contains(value: BigDecimal?): Boolean =
        !active || value != null && (lower?.let { value >= it } ?: true) && (upper?.let { value <= it } ?: true)
}

internal data class MenuFilter(
    val query: String = "",
    val families: Set<String> = emptySet(),
    val styles: Set<String> = emptySet(),
    val breweries: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val tried: Tried = Tried.ALL,
    val abv: MenuRange = MenuRange(),
    val ibu: MenuRange = MenuRange(),
    val ratingMin: String = "",
    val price: MenuRange = MenuRange(),
    val currency: String? = null,
    val available: Boolean = false,
    val sort: MenuSort = MenuSort.MENU,
    val volumeMl: MenuRange = MenuRange(),
) {
    fun valid(): Boolean = abv.valid(100.toBigDecimal()) && ibu.valid(1000.toBigDecimal()) &&
        MenuRange(min = ratingMin).valid(5.toBigDecimal()) &&
        volumeMl.valid(100000.toBigDecimal()) && price.valid(BigDecimal("9999999999.99")) && (!price.active || currency != null)

    fun encode(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("query", query)
        put("families", JSONArray(families.sorted()))
        put("styles", JSONArray(styles.sorted()))
        put("breweries", JSONArray(breweries.sorted()))
        put("countries", JSONArray(countries.sorted()))
        put("formats", JSONArray(formats.sorted()))
        put("tried", tried.name)
        put("abvMin", abv.min); put("abvMax", abv.max)
        put("ibuMin", ibu.min); put("ibuMax", ibu.max)
        put("volumeMin", volumeMl.min); put("volumeMax", volumeMl.max)
        put("ratingMin", ratingMin)
        put("priceMin", price.min); put("priceMax", price.max)
        put("currency", currency)
        put("available", available)
        put("sort", sort.name)
    }

    companion object {
        fun decode(data: JSONObject): MenuFilter {
            require(data.getInt("version") == 1) { "Версия фильтра не поддерживается" }
            fun set(key: String): Set<String> = data.getJSONArray(key).let { array ->
                (0 until array.length()).map(array::getString).toSet()
            }
            return MenuFilter(
                query = data.getString("query"),
                families = set("families"), styles = set("styles"), breweries = set("breweries"),
                countries = set("countries"), formats = set("formats"), tried = Tried.valueOf(data.getString("tried")),
                abv = MenuRange(data.getString("abvMin"), data.getString("abvMax")),
                ibu = MenuRange(data.getString("ibuMin"), data.getString("ibuMax")),
                volumeMl = MenuRange(data.optString("volumeMin", ""), data.optString("volumeMax", "")),
                ratingMin = data.getString("ratingMin"), price = MenuRange(data.getString("priceMin"), data.getString("priceMax")),
                currency = if (data.isNull("currency")) null else data.getString("currency"),
                available = data.getBoolean("available"), sort = MenuSort.valueOf(data.getString("sort")),
            ).also { require(it.valid()) { "Некорректные границы фильтра" } }
        }
    }
}

internal fun formatKey(value: String): String = when (val key = normalized(value)) {
    "банка", "can" -> "can"
    "бутылка", "bottle" -> "bottle"
    "кран", "разливное", "draft", "draught", "tap", "keg" -> "draft"
    else -> key
}

internal fun formatTitle(value: String): String = when (value) {
    "can" -> "Банка"
    "bottle" -> "Бутылка"
    "draft" -> "Кран"
    else -> value
}

internal fun MenuPosition.formatKeys(): Set<String> = setOfNotNull(packaging?.let(::formatKey), servingFormat?.let(::formatKey))

internal fun filterMenu(rows: List<MenuPosition>, filter: MenuFilter, relevance: Map<String, Int> = emptyMap()): List<MenuPosition> {
    val query = normalized(filter.query).split(' ').filter(String::isNotEmpty)
    val rating = MenuRange(min = filter.ratingMin)
    val matches = rows.filter { row ->
        (filter.families.isEmpty() && filter.styles.isEmpty() ||
            MenuStyles.classify(row.style)?.family in filter.families || MenuStyles.key(row) in filter.styles) &&
            (filter.breweries.isEmpty() || row.breweryId in filter.breweries) &&
            (filter.countries.isEmpty() || row.country?.let(::normalized) in filter.countries) &&
            (filter.formats.isEmpty() || row.formatKeys().any { it in filter.formats }) &&
            (filter.tried == Tried.ALL || row.tried == (filter.tried == Tried.YES)) &&
            (!filter.available || row.available) &&
            (!filter.abv.active || filter.abv.contains(row.abv?.toBigDecimal())) &&
            (!filter.ibu.active || filter.ibu.contains(row.ibu?.toBigDecimal())) &&
            (!filter.volumeMl.active || filter.volumeMl.contains(row.volumeMl?.toBigDecimal())) &&
            (!rating.active || rating.contains(row.averageRating?.toBigDecimal())) &&
            (!filter.price.active || row.currency == filter.currency && filter.price.contains(row.price)) &&
            query.all { it in normalized(row.name) }
    }
    val comparator = when (filter.sort) {
        MenuSort.MENU -> return matches
        MenuSort.NAME -> compareBy<MenuPosition> { normalized(it.name) }
        MenuSort.STYLE -> ascending { it.style?.let(::normalized) }
        MenuSort.BREWERY -> compareBy<MenuPosition> { normalized(it.brewery) }
        MenuSort.ABV_ASC -> ascending { it.abv }
        MenuSort.ABV_DESC -> descending { it.abv }
        MenuSort.IBU_ASC -> ascending { it.ibu }
        MenuSort.IBU_DESC -> descending { it.ibu }
        MenuSort.RATING -> descending { it.averageRating }
        MenuSort.PRICE -> ascending { it.currency }.then(ascending { it.price })
        MenuSort.NEW_FIRST -> compareBy<MenuPosition> { it.tried }
        MenuSort.RELEVANCE -> descending { relevance[it.positionId] }.then(descending { it.publicAverageRating })
    }
    return matches.sortedWith(comparator.thenBy { it.positionId })
}

private fun <T : Comparable<T>> ascending(value: (MenuPosition) -> T?): Comparator<MenuPosition> =
    compareBy<MenuPosition> { value(it) == null }.thenBy(value)

private fun <T : Comparable<T>> descending(value: (MenuPosition) -> T?): Comparator<MenuPosition> =
    compareBy<MenuPosition> { value(it) == null }.thenByDescending(value)
