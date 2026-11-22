package ru.krapiva.app.offline

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AdvisorTest {
    private val start = Instant.parse("2026-11-21T00:00:00Z")
    private val breweries = listOf("Zagovor", "Big Village", "Velka Morava", "Other")

    private fun observation(
        index: Int, brewery: String = "Zagovor", position: String = "tried-$index",
        style: String = "style-$brewery", rating: Int? = null, first: Boolean = true,
    ) = AdvisorCheckin("entry-$index", position, brewery, style, start.plusSeconds(index.toLong()), rating, first)

    private fun candidate(
        id: String, brewery: String, style: String? = "style-$brewery", rating: Double? = null, tried: Boolean = false,
    ) = AdvisorCandidate(RecommendationCard(id, "Beer $id", brewery, style), brewery, style, tried, rating)

    private fun scenario(lowRatings: Boolean): List<AdvisorCheckin> =
        breweries.flatMapIndexed { group, brewery ->
            (0 until if (group == 3) 10 else 30).map { offset ->
                observation(group * 30 + offset, brewery, rating = when {
                    group == 0 && lowRatings -> 4
                    group == 1 -> 17
                    else -> null
                })
            }
        }

    @Test fun exampleThirtyThirtyThirtyTenAndNegativeRatings() {
        val offers = breweries.flatMap { brewery -> listOf(candidate("$brewery-1", brewery), candidate("$brewery-2", brewery)) }
        val baseline = LocalDiscovery.advisorProfile(scenario(false))
        val first = LocalDiscovery.advise(offers, baseline, AdvisorMode.NEW)
        assertEquals(breweries.take(3).toSet(), first.take(3).map { it.candidate.breweryId }.toSet())
        assertTrue(first.take(3).all { row ->
            row.explanation.any { it.contains(row.candidate.breweryId) && it.contains("30") && it.contains("100") }
        })
        val lowered = LocalDiscovery.advise(offers, LocalDiscovery.advisorProfile(scenario(true)), AdvisorMode.NEW)
        assertTrue(lowered.take(3).none { it.candidate.breweryId == "Zagovor" })
        assertTrue(lowered.first { it.candidate.breweryId == "Zagovor" }.score < first.first { it.candidate.breweryId == "Zagovor" }.score)
    }

    @Test fun exactWindowsFromZeroToOverOneHundred() {
        for (size in listOf(0, 1, 10, 99, 100, 116)) {
            val raw = (0 until size).map { observation(it) }
            val profile = LocalDiscovery.advisorProfile(raw.reversed())
            assertEquals(size.coerceAtMost(100), profile.historySize)
            assertEquals(raw.takeLast(100).map { it.positionId }.toSet(), profile.triedIds)
            assertEquals(size >= 3, profile.personalized)
        }
        val edge = observation(0)
        val sameTime = edge.copy(id = "entry-z", positionId = "same-time")
        val latest = (1..99).map { observation(it) }
        val profile = LocalDiscovery.advisorProfile(listOf(sameTime, edge) + latest + sameTime)
        assertFalse(edge.positionId in profile.triedIds)
        assertTrue(sameTime.positionId in profile.triedIds)
    }

    @Test fun repeatedCheckinsHaveBoundedContributionAndHabitUsesActualFirsts() {
        val position = "same-package"
        val first = (97..100).map { observation(it, position = position, rating = 20, first = it == 97) }
        val excessive = (0..96).map { observation(it, position = position, rating = 1, first = it == 0) } +
            first.map { it.copy(firstForPosition = false) }
        val short = LocalDiscovery.advisorProfile(first)
        val long = LocalDiscovery.advisorProfile(excessive)
        assertEquals(short.breweries, long.breweries)
        assertEquals(1, long.breweries.getValue("Zagovor").unique)
        assertEquals(2, long.breweries.getValue("Zagovor").repeats)
        assertTrue(short.noveltyBonus > long.noveltyBonus)
    }

    @Test fun newAndSurpriseUseActualPositionIdentityAndStyleHistory() {
        val journal = (1..10).map { observation(it, style = "familiar") }
        val profile = LocalDiscovery.advisorProfile(journal)
        val tried = candidate("tried-3", "Zagovor", "familiar")
        val only = candidate("new-package", "unfamiliar", "rare")
        val allTime = candidate("older-than-window", "unfamiliar", "rare", tried = true)
        val new = LocalDiscovery.advise(listOf(tried, only, allTime), profile, AdvisorMode.NEW)
        assertEquals(listOf("new-package"), new.map { it.candidate.card.id })
        assertTrue(LocalDiscovery.advise(listOf(tried), profile, AdvisorMode.NEW).isEmpty())
        val surprise = LocalDiscovery.advise(listOf(candidate("same-style", "Zagovor", "familiar"), only), profile, AdvisorMode.SURPRISE)
        assertEquals(only, surprise.first().candidate)
        assertTrue(surprise.first().explanation.any { it.contains("Этого стиля нет") })
    }

    @Test fun missingServerHistoryAndShortHistoryUseNeutralPublicRanking() {
        val rows = listOf(candidate("unknown-rating", "Zagovor"), candidate("public", "Other", rating = 4.0))
        for (history in listOf(null, emptyList(), listOf(observation(1, rating = 20)))) {
            val profile = LocalDiscovery.advisorProfile(history)
            assertEquals(history?.size, profile.historySize)
            val ranked = LocalDiscovery.advise(rows, profile, AdvisorMode.MY_CHOICE)
            assertEquals("public", ranked.first().candidate.card.id)
            assertTrue(ranked.all { it.explanation.any { reason -> reason.startsWith("Нейтральный") } })
            assertTrue(ranked.last().explanation.none { it.contains("рейтинг") })
        }
    }

    @Test fun resultAndExplanationsAreDeterministicAndReactToEditsAndDeletes() {
        val history = scenario(false)
        val rows = breweries.map { candidate(it, it) }
        val initial = LocalDiscovery.advise(rows, LocalDiscovery.advisorProfile(history), AdvisorMode.MY_CHOICE)
        assertEquals(initial, LocalDiscovery.advise(rows.reversed(), LocalDiscovery.advisorProfile(history.reversed()), AdvisorMode.MY_CHOICE))
        val edited = history.filterNot { it.positionId == "tried-0" }
            .map { if (it.breweryId == "Zagovor") it.copy(ratingQuarters = 3) else it }
        val updated = LocalDiscovery.advise(rows, LocalDiscovery.advisorProfile(edited), AdvisorMode.MY_CHOICE)
        val recent = updated.first { it.candidate.breweryId == "Zagovor" }
        assertTrue(recent.explanation.any { it.contains("29") && it.contains("99") })
        assertTrue(recent.explanation.any { it.contains("Низкие личные оценки") })
    }
}
