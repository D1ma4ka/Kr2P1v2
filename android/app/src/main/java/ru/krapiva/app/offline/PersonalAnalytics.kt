package ru.krapiva.app.offline

import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

data class PersonalCheckin(
    val id: String, val positionId: String, val name: String, val brewery: String?,
    val style: String?, val quarters: Int?, val occurredAt: Instant, val version: Long,
    val tags: List<String> = emptyList()
)
data class TasteSummary(
    val checkins: Int, val uniquePositions: Int, val average: Double?,
    val styles: List<Pair<String,Int>>, val breweries: List<Pair<String,Int>>,
    val histogram: List<Pair<Int,Int>>, val tags: List<Pair<String,Int>>,
    val monthlyStyles: Map<YearMonth,List<Pair<String,Int>>>, val top: List<PersonalCheckin>
)
object PersonalAnalytics {
    fun current(records: List<PersonalCheckin>): List<PersonalCheckin> = records.groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
    private fun counts(values: List<String>): List<Pair<String,Int>> = values.groupingBy { it }.eachCount().toList().sortedWith(compareByDescending<Pair<String,Int>> { it.second }.thenBy { it.first })
    fun summarize(records: List<PersonalCheckin>, zone: ZoneId, year: Int? = null): TasteSummary {
        val rows=current(records).filter { year == null || it.occurredAt.atZone(zone).year == year }
        val rated=rows.mapNotNull { it.quarters?.takeIf { q -> q in 1..20 } }
        val months=rows.groupBy { YearMonth.from(it.occurredAt.atZone(zone)) }.toSortedMap()
        return TasteSummary(rows.size,rows.map { it.positionId }.distinct().size,
            rated.takeIf { it.isNotEmpty() }?.average()?.div(4.0),
            counts(rows.map { it.style ?: "Неизвестный стиль" }),counts(rows.mapNotNull { it.brewery }),
            (1..20).map { q -> q to rated.count { it == q } },counts(rows.flatMap { it.tags.distinct() }),
            if(rows.size >= 5 && months.size >= 2)months.mapValues { counts(it.value.mapNotNull { row -> row.style }) } else emptyMap(),
            rows.filter { it.quarters != null }.groupBy { it.positionId }.values.map { it.maxBy { row -> row.quarters ?: 0 } }.sortedWith(compareByDescending<PersonalCheckin> { it.quarters }.thenBy { it.name }).take(5))
    }
}
