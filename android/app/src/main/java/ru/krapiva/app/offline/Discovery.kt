package ru.krapiva.app.offline

/** Domain recommendation contract; no server URL or fabricated response. */
data class RecommendationCard(val id: String, val name: String, val brewery: String?, val style: String?)
data class LocalRecommendation(val card: RecommendationCard, val reason: String, val score: Int)
interface FutureRecommendationSource { suspend fun recommendations(): List<RecommendationCard> }
object LocalDiscovery {
    fun select(cards: List<RecommendationCard>, history: List<PersonalCheckin>): List<LocalRecommendation> {
        val rows=PersonalAnalytics.current(history)
        val tried=rows.map{it.positionId}.toSet()
        val liked=rows.filter{(it.quarters ?: 0)>=16}
        val styles=liked.mapNotNull{it.style}.groupingBy{it}.eachCount()
        val breweries=liked.mapNotNull{it.brewery}.groupingBy{it}.eachCount()
        return cards.distinctBy{it.id}.filter{it.id !in tried}.mapNotNull { card ->
            val style=styles[card.style] ?: 0;val brewery=breweries[card.brewery] ?: 0
            if(style+brewery==0)null else LocalRecommendation(card,
                listOfNotNull(if(style>0)"Стиль встречался в ваших оценках от 4,00" else null,if(brewery>0)"Пивоварня встречалась в ваших оценках от 4,00" else null).joinToString(" · "),style*2+brewery)
        }.sortedWith(compareByDescending<LocalRecommendation>{it.score}.thenBy{it.card.id}).take(20)
    }
}
