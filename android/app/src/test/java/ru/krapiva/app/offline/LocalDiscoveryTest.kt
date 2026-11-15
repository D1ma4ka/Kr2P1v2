package ru.krapiva.app.offline
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class LocalDiscoveryTest {
    @Test fun selectsOnlyRealUntriedCardsWithSupportedReasons() {
        val history=listOf(PersonalCheckin("1","tried","Beer","Brewery","IPA",18,Instant.EPOCH,1))
        val cards=listOf(RecommendationCard("tried","A","Brewery","IPA"),RecommendationCard("can","B","Other","IPA"),RecommendationCard("bottle","C","Brewery",null),RecommendationCard("unknown","D",null,null))
        assertEquals(listOf("can","bottle"),LocalDiscovery.select(cards,history).map{it.card.id})
        assertTrue(LocalDiscovery.select(cards,emptyList()).isEmpty())
        assertTrue(LocalDiscovery.select(emptyList(),history).isEmpty())
        assertTrue(LocalDiscovery.select(cards,history.map{it.copy(quarters=null)}).isEmpty())
    }
}
