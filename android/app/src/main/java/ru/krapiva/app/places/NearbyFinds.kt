package ru.krapiva.app.places

import org.json.JSONObject
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import ru.krapiva.app.menu.BarMenu
import ru.krapiva.app.menu.MenuFilter
import java.time.Instant

internal enum class FindMode(val wire: String, val title: String) {
    WISHLIST("wishlist", "В моём wishlist"),
    UNTRIED("untried", "Ещё не пробовал"),
    FAVORITE("favorite", "Мои любимые стили"),
    RECOMMENDED("recommended", "Рекомендовано мне"),
    FILTER("filter", "Мой фильтр"),
}

internal enum class FindSort(val wire: String, val title: String) {
    DISTANCE("distance", "Ближе к точке поиска"),
    MATCHES("matches", "Больше совпадений"),
    UNTRIED("untried", "Больше непробованных позиций"),
    WISHLIST("wishlist", "Больше совпадений с wishlist"),
    RELEVANCE("relevance", "Лучше соответствие предпочтениям"),
    FRESHNESS("freshness", "Самое свежее меню"),
}

internal data class FindConditions(
    val origin: Coordinates,
    val radius: Int,
    val mode: FindMode,
    val filter: MenuFilter? = null,
) {
    fun request(): JSONObject = origin.request(radius).put("mode", mode.wire).apply {
        filter?.let { put("filter", it.encode()) }
    }

    fun search(sort: FindSort, cursor: String?): JSONObject = request()
        .put("sort", sort.wire).apply { cursor?.let { put("cursor", it) } }
}

internal enum class FindMenuStatus(val wire: String, val title: String) {
    CURRENT("current", "Опубликованное меню"),
    STALE("stale", "Редакция устарела. Наличие могло измениться."),
    UNPUBLISHED("unpublished", "Меню пока не опубликовано."),
    EMPTY("empty", "В опубликованном меню нет позиций."),
}

internal data class FindMatch(
    val positionId: String,
    val wished: Boolean,
    val reasons: List<String>,
) {
    val explanation: String get() = reasons.mapNotNull {
        when (it) {
            "wishlist" -> "Из wishlist"
            "untried" -> "Ещё не пробовал"
            "favorite" -> "Любимый стиль или семейство"
            "advisor" -> "Подбор KraPiva Advisor"
            "neutral_advisor" -> "Нейтральный подбор Advisor"
            "advisor_brewery" -> "Учтён твой опыт с пивоварней"
            "advisor_style" -> "Учтена твоя история по стилю"
            "advisor_public_rating" -> "Учтён публичный рейтинг"
            "filter" -> "Подходит под фильтр"
            else -> null
        }
    }.joinToString(" · ")

    companion object {
        fun read(row: JSONObject): FindMatch {
            val reasons = row.getJSONArray("reasons")
            return FindMatch(row.getString("positionId"), row.getBoolean("wished"),
                (0 until reasons.length()).map(reasons::getString))
        }
    }
}

internal data class FindVenue(
    val id: String,
    val name: String,
    val type: String,
    val city: String,
    val coordinates: Coordinates,
    val distanceKm: Double,
    val status: FindMenuStatus,
    val updatedAt: Instant?,
    val matches: Int,
    val untried: Int,
    val wishlist: Int,
    val favorite: Int,
    val styles: List<String>,
    val untriedStyle: Pair<String, Int>?,
) {
    fun point(): JSONObject = JSONObject().put("id", id).put("type", type).put("matchCount", matches)
        .put("latitude", coordinates.latitude).put("longitude", coordinates.longitude)

    companion object {
        fun read(row: JSONObject): FindVenue {
            val venue = row.getJSONObject("venue")
            val styles = row.getJSONArray("styles")
            return FindVenue(
                venue.getString("id"), venue.getString("name"), venue.getString("type"), venue.getString("city"),
                Coordinates(venue.getDouble("latitude"), venue.getDouble("longitude")), venue.getDouble("distanceKm"),
                FindMenuStatus.entries.first { it.wire == venue.getString("status") },
                venue.text("updatedAt")?.let(Instant::parse), row.getInt("matches"), row.getInt("untried"),
                row.getInt("wishlist"), row.getInt("favorite"), (0 until styles.length()).map(styles::getString),
                row.optJSONObject("untriedStyle")?.let { it.getString("name") to it.getInt("count") },
            )
        }
    }
}

internal data class FindPage(
    val items: List<FindVenue>,
    val next: String?,
    val count: Int,
    val historySize: Int,
    val personalized: Boolean,
) {
    fun append(page: FindPage): FindPage = page.copy(items = (items + page.items).distinctBy { it.id })

    companion object {
        fun read(body: JSONObject): FindPage = FindPage(
            body.getJSONArray("items").objects().map(FindVenue::read), body.text("nextCursor"),
            body.getJSONObject("totals").getInt("withMatches"), body.getInt("historySize"),
            body.getBoolean("personalized"),
        )
    }
}

internal data class FindMenu(val venue: FindVenue, val menu: BarMenu?, val matches: List<FindMatch>) {
    companion object {
        fun read(body: JSONObject): FindMenu = FindMenu(
            FindVenue.read(body.getJSONObject("summary")), BarMenu.read(body),
            body.getJSONArray("matches").objects().map(FindMatch::read),
        )
    }
}
