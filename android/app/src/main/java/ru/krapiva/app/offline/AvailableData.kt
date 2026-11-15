package ru.krapiva.app.offline

import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import ru.krapiva.app.diary.strings
import java.time.Instant

data class AvailableData(val checkins: List<PersonalCheckin>, val positions: List<JSONObject>, val wishlist: Set<String>)
interface AvailableCardsSource { suspend fun snapshot(): AvailableData }
class CachedPersonalSource(private val api: AccountApi): AvailableCardsSource {
    override suspend fun snapshot(): AvailableData {
        val owner=api.ownerId() ?: return AvailableData(emptyList(),emptyList(),emptySet())
        val records=mutableListOf<PersonalCheckin>();val cards=linkedMapOf<String,JSONObject>();val wishlist=mutableSetOf<String>()
        fun record(row: JSONObject) {
            if(row.text("userId") != owner || row.text("positionId") == null || row.text("id") == null) return
            val time=runCatching { Instant.parse(row.getString("occurredAt")) }.getOrNull() ?: return
            records.add(PersonalCheckin(row.getString("id"),row.getString("positionId"),row.optString("positionName"),row.text("breweryName"),row.text("styleName"),
                if(row.isNull("ratingQuarters"))null else row.getInt("ratingQuarters").takeIf { it in 1..20 },time,row.optLong("version"),row.optJSONArray("tagLabels")?.strings().orEmpty()))
        }
        fun card(row: JSONObject) {
            if(row.text("name") != null && row.text("breweryName") != null && row.text("id") != null) cards[row.getString("id")]=row
        }
        api.availableCache().forEach { (path,data) ->
            when {
                path.startsWith("v1/checkins?") -> data.optJSONArray("items")?.objects()?.forEach(::record)
                path.startsWith("v1/checkins/") -> record(data)
                path=="v1/me/home" -> data.optJSONObject("recent")?.optJSONArray("items")?.objects()?.forEach(::record)
                path.startsWith("v1/positions?") -> data.optJSONArray("items")?.objects()?.forEach(::card)
                path.startsWith("v1/positions/") && !path.endsWith("/statistics") -> card(data)
                path.startsWith("v1/me/wishlist?") -> data.optJSONArray("items")?.objects()?.forEach { row -> card(row);row.text("id")?.let(wishlist::add) }
            }
        }
        check(api.ownerId()==owner) { "Аккаунт изменился" }
        return AvailableData(PersonalAnalytics.current(records),cards.values.toList(),wishlist)
    }
}
