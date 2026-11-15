package ru.krapiva.app.catalog

import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.diary.strings

internal fun recentValues(old: List<String>, value: String, limit: Int = 20): List<String> =
    if(value.isBlank()) old.take(limit) else (listOf(value.trim()) + old.filterNot { it.equals(value.trim(),ignoreCase=true) }).take(limit)

class CatalogHistory(private val api: AccountApi) {
    suspend fun read() = api.localDocument("catalog-history-v1")
    suspend fun search(filters: CatalogFilters) {
        api.editLocalDocument("catalog-history-v1") { data ->
            data.put("queries",JSONArray(recentValues(data.optJSONArray("queries")?.strings().orEmpty(),filters.q)))
            data.put("filters",JSONObject().apply {
                for((key,value) in listOf("q" to filters.q,"sort" to filters.sort,"breweryId" to filters.breweryId,
                    "breweryLabel" to filters.breweryLabel,"styleId" to filters.styleId,"styleLabel" to filters.styleLabel,
                    "packaging" to filters.packaging,"abvMin" to filters.abvMin,"abvMax" to filters.abvMax)) put(key,value)
            })
        }
    }
    suspend fun viewed(id: String) { api.editLocalDocument("catalog-history-v1") { data -> data.put("recent",JSONArray(recentValues(data.optJSONArray("recent")?.strings().orEmpty(),id))) } }
    suspend fun clear() { api.editLocalDocument("catalog-history-v1") { it.put("queries",JSONArray()).put("recent",JSONArray()) } }
    fun filters(data: JSONObject): CatalogFilters {
        val f=data.optJSONObject("filters") ?: return CatalogFilters()
        return CatalogFilters(f.optString("q"),f.optString("sort","newest"),f.optString("breweryId"),f.optString("breweryLabel"),f.optString("styleId"),f.optString("styleLabel"),f.optString("packaging"),f.optString("abvMin"),f.optString("abvMax"))
    }
}
