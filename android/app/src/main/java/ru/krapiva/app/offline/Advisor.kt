package ru.krapiva.app.offline

import java.time.Instant
import kotlin.math.roundToInt

internal data class AdvisorCheckin(
    val id: String,
    val positionId: String,
    val breweryId: String,
    val styleId: String?,
    val occurredAt: Instant,
    val ratingQuarters: Int?,
    val firstForPosition: Boolean,
)

internal data class AdvisorCandidate(
    val card: RecommendationCard,
    val breweryId: String,
    val styleId: String?,
    val tried: Boolean,
    val publicRating: Double?,
)

internal enum class AdvisorMode(val title: String) {
    MY_CHOICE("Мой выбор"),
    NEW("Новое для меня"),
    SURPRISE("Удиви меня"),
}

internal object AdvisorWeights {
    const val WINDOW = 100
    const val MIN_HISTORY = 3
    const val TOP = 3
    const val NEUTRAL_RATING = 12
    const val RATING_SMOOTHING = 200
    val REPEAT_WEIGHTS = listOf(100, 25, 10)
    const val BREWERY_UNIQUE = 18
    const val BREWERY_REPEAT = 6
    const val STYLE_UNIQUE = 11
    const val BREWERY_RATING = 3
    const val STYLE_RATING = 1
    const val POSITION_RATING = 2
    const val MAX_POSITIVE_RATING_BONUS = 600
    const val NOVELTY = 50
    const val NOVELTY_HABIT = 50
    const val PUBLIC_RATING = 10
    const val RARE_STYLE = 20
    const val UNFAMILIAR_BREWERY = 70
    const val DIVERSIFY_BREWERY = 700
    const val DIVERSIFY_STYLE = 100
}

internal data class AdvisorAffinity(
    val unique: Int = 0,
    val repeats: Int = 0,
    val recent: Int = 0,
    val judgment: Int = 0,
)

internal data class AdvisorProfile(
    val historySize: Int?,
    val triedIds: Set<String>,
    val noveltyBonus: Int,
    val breweries: Map<String, AdvisorAffinity>,
    val styles: Map<String, AdvisorAffinity>,
    val positions: Map<String, AdvisorAffinity>,
) {
    val personalized get() = (historySize ?: 0) >= AdvisorWeights.MIN_HISTORY
}

internal data class AdvisorRecommendation(
    val candidate: AdvisorCandidate,
    val tried: Boolean,
    val score: Int,
    val explanation: List<String>,
)

private data class RecentCheckin(val row: AdvisorCheckin, val recency: Int)

internal fun profileForAdvisor(history: List<AdvisorCheckin>?): AdvisorProfile {
    val rows = history.orEmpty().sortedWith(compareByDescending<AdvisorCheckin> { it.occurredAt }.thenByDescending { it.id })
        .distinctBy(AdvisorCheckin::id).take(AdvisorWeights.WINDOW)
    val recent = rows.mapIndexed { index, row -> RecentCheckin(row, AdvisorWeights.WINDOW - index / 2) }
    val historyHabit = AdvisorWeights.NOVELTY_HABIT * rows.count { it.firstForPosition } / rows.size.coerceAtLeast(1)
    return AdvisorProfile(history?.let { rows.size }, rows.map { it.positionId }.toSet(), AdvisorWeights.NOVELTY + historyHabit,
        recent.groupBy { it.row.breweryId }.mapValues { affinity(it.value) },
        recent.filter { it.row.styleId != null }.groupBy { requireNotNull(it.row.styleId) }.mapValues { affinity(it.value) },
        recent.groupBy { it.row.positionId }.mapValues { affinity(it.value) })
}

private fun affinity(rows: List<RecentCheckin>): AdvisorAffinity {
    val positions = rows.groupBy { it.row.positionId }
    var ratedWeight = 0
    var ratedSum = 0
    var repeats = 0
    for (position in positions.values) {
        repeats += (position.size - 1).coerceAtMost(AdvisorWeights.REPEAT_WEIGHTS.size - 1)
        for ((index, entry) in position.take(AdvisorWeights.REPEAT_WEIGHTS.size).withIndex()) {
            val rating = entry.row.ratingQuarters ?: continue
            val weight = entry.recency * AdvisorWeights.REPEAT_WEIGHTS[index] / 100
            ratedWeight += weight
            ratedSum += weight * (rating - AdvisorWeights.NEUTRAL_RATING)
        }
    }
    return AdvisorAffinity(positions.size, repeats, rows.first().recency,
        100 * ratedSum / (ratedWeight + AdvisorWeights.RATING_SMOOTHING))
}

internal fun rankAdvisorCandidates(
    candidates: List<AdvisorCandidate>,
    profile: AdvisorProfile,
    mode: AdvisorMode,
): List<AdvisorRecommendation> {
    val ranked = candidates.distinctBy { it.card.id }.mapNotNull { candidate ->
        val tried = candidate.tried || candidate.card.id in profile.triedIds
        if (mode == AdvisorMode.NEW && tried) return@mapNotNull null
        val beerRating = candidate.publicRating?.let { (it * AdvisorWeights.PUBLIC_RATING).roundToInt() } ?: 0
        val novelty = if (tried) 0 else profile.noveltyBonus
        val brewery = profile.breweries[candidate.breweryId] ?: AdvisorAffinity()
        val style = profile.styles[candidate.styleId] ?: AdvisorAffinity()
        val position = profile.positions[candidate.card.id] ?: AdvisorAffinity()
        val personalRatings = AdvisorWeights.BREWERY_RATING * brewery.judgment +
            AdvisorWeights.STYLE_RATING * style.judgment + AdvisorWeights.POSITION_RATING * position.judgment
        val affinity = if (mode == AdvisorMode.SURPRISE) {
            val rareStyle = if (candidate.styleId == null) 0 else (15 - style.unique.coerceAtMost(15)) * AdvisorWeights.RARE_STYLE
            rareStyle + if (brewery.unique == 0) AdvisorWeights.UNFAMILIAR_BREWERY else 0
        } else {
            brewery.unique.coerceAtMost(30) * AdvisorWeights.BREWERY_UNIQUE +
                brewery.repeats.coerceAtMost(12) * AdvisorWeights.BREWERY_REPEAT + brewery.recent / 2 +
                style.unique.coerceAtMost(15) * AdvisorWeights.STYLE_UNIQUE + style.recent / 3
        }
        val score = if (profile.personalized) beerRating + novelty + affinity + personalRatings.coerceAtMost(AdvisorWeights.MAX_POSITIVE_RATING_BONUS) else beerRating
        val reasons = buildList {
            if (!profile.personalized) {
                add("Нейтральный выбор из доступного меню.")
            } else {
                if (brewery.unique > 0) add("Ваш опыт с пивоварней «${candidate.card.brewery.orEmpty()}» · разных позиций — ${brewery.unique}. Учтено чекинов — ${profile.historySize}.")
                if (style.unique > 0) add("В этом стиле в окне истории · ${style.unique} разных позиций.")
                if (brewery.judgment < 0) add("Низкие личные оценки этой пивоварни снижают приоритет.")
                else if (brewery.judgment > 0) add("Личные оценки этой пивоварни повышают приоритет.")
                if (style.judgment < 0 || position.judgment < 0) add("Оценки стиля или позиции снижают приоритет.")
                if (mode == AdvisorMode.SURPRISE && candidate.styleId != null && style.unique == 0) add("Этого стиля нет в недавней истории.")
                if (mode == AdvisorMode.SURPRISE && brewery.unique == 0) add("Этой пивоварни нет в недавней истории.")
            }
            add(if (tried) "Эту позицию уже пробовали." else "Эту позицию ещё не пробовали по данным меню.")
            if (candidate.publicRating != null) add("Учтён публичный рейтинг позиции.")
        }
        AdvisorRecommendation(candidate, tried, score, reasons)
    }.sortedWith(compareByDescending<AdvisorRecommendation> { it.score }.thenBy { it.candidate.card.id })

    val remaining = ranked.toMutableList()
    val top = mutableListOf<AdvisorRecommendation>()
    while (top.size < AdvisorWeights.TOP && remaining.isNotEmpty()) {
        // The pre-sorted list supplies a stable tie break.
        val best = remaining.maxBy { row ->
            val sameBrewery = top.count { it.candidate.breweryId == row.candidate.breweryId }
            val sameStyle = top.count { row.candidate.styleId != null && it.candidate.styleId == row.candidate.styleId }
            row.score - AdvisorWeights.DIVERSIFY_BREWERY * sameBrewery - AdvisorWeights.DIVERSIFY_STYLE * sameStyle
        }
        top += best
        remaining.remove(best)
    }
    return top + remaining
}
