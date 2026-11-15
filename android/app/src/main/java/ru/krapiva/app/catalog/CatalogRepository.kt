package ru.krapiva.app.catalog
import android.net.Uri
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi

class CatalogRepository(private val api: AccountApi) {
    val history = CatalogHistory(api)
    suspend fun read(path: String) = api.catalogGet(path)
    suspend fun lookup(code: String, kind: String) = api.json("v1/barcodes/lookup?code=${Uri.encode(code)}&kind=$kind")
    suspend fun propose(data: JSONObject) = api.json("v1/position-proposals", "POST", data)
    suspend fun edit(id: String, data: JSONObject) = api.json("v1/position-proposals/$id", "PATCH", data)
    suspend fun photo(id: String, offline: Boolean) = api.photo(id, offline)
}
fun JSONObject.text(field: String): String? = if (isNull(field)) null else optString(field).takeIf { it.isNotBlank() && it != "null" }
