package ru.krapiva.app.offline

import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import java.time.Instant
import java.util.UUID

/** Future server sync consumes dirty records including tombstones; no endpoint is called. */
interface CollectionSyncSource { suspend fun pendingCollections(): List<JSONObject> }
class CollectionsRepository(private val api: AccountApi) : CollectionSyncSource {
    private val key = "collections-v1"
    suspend fun all(): List<JSONObject> = api.localDocument(key).optJSONArray("items")?.objects().orEmpty().filter { !it.optBoolean("deleted") }
    override suspend fun pendingCollections(): List<JSONObject> = api.localDocument(key).optJSONArray("items")?.objects().orEmpty().filter { it.optBoolean("dirty") }
    private fun stamp(record: JSONObject) {
        record.put("updatedAt",Instant.now().toString()).put("localVersion",record.optLong("localVersion")+1).put("dirty",true)
    }
    suspend fun create(name: String) {
        val title = name.trim(); require(title.isNotEmpty() && title.length <= 80) { "Название: от 1 до 80 символов" }
        api.editLocalDocument(key) { data ->
            val rows = data.optJSONArray("items") ?: JSONArray()
            val now = Instant.now().toString()
            rows.put(JSONObject().put("id",UUID.randomUUID().toString()).put("ownerUserId",api.ownerId())
                .put("name",title).put("createdAt",now).put("updatedAt",now).put("localVersion",1)
                .put("dirty",true).put("deleted",false).put("members",JSONArray()))
            data.put("schemaVersion",1).put("items",rows)
        }
    }
    private suspend fun mutate(id: String, change: (JSONObject) -> Unit) {
        api.editLocalDocument(key) { data ->
            val row = data.getJSONArray("items").objects().first { it.getString("id") == id && !it.optBoolean("deleted") }
            change(row); stamp(row)
        }
    }
    suspend fun rename(id: String, name: String) {
        val title = name.trim(); require(title.isNotEmpty() && title.length <= 80) { "Название: от 1 до 80 символов" }
        mutate(id) { it.put("name",title) }
    }
    suspend fun delete(id: String) = mutate(id) { it.put("deleted",true) }
    private fun putMember(row: JSONObject, positionId: String) {
        val members = row.getJSONArray("members")
        val existing = members.objects().firstOrNull { it.getString("positionId") == positionId }
        if(existing == null) {
            val now = Instant.now().toString()
            members.put(JSONObject().put("id",UUID.randomUUID().toString()).put("ownerUserId",api.ownerId())
                .put("positionId",positionId).put("createdAt",now).put("updatedAt",now)
                .put("localVersion",1).put("dirty",true).put("deleted",false))
        } else if(existing.optBoolean("deleted")) { existing.put("deleted",false);stamp(existing) }
    }
    suspend fun add(id: String, positionId: String) = mutate(id) { putMember(it,positionId) }
    suspend fun remove(id: String, positionId: String) = mutate(id) { row ->
        row.getJSONArray("members").objects().firstOrNull { it.getString("positionId") == positionId }?.let { it.put("deleted",true);stamp(it) }
    }
    suspend fun move(from: String, to: String, positionId: String, copy: Boolean) {
        require(from != to)
        api.editLocalDocument(key) { data ->
            val rows = data.getJSONArray("items").objects().filter { !it.optBoolean("deleted") }
            val source = rows.first { it.getString("id") == from };val target = rows.first { it.getString("id") == to }
            val member = source.getJSONArray("members").objects().first { it.getString("positionId") == positionId && !it.optBoolean("deleted") }
            putMember(target,positionId);stamp(target)
            if(!copy) { member.put("deleted",true);stamp(member);stamp(source) }
        }
    }
    suspend fun reorder(id: String, positionId: String, delta: Int) = mutate(id) { row ->
        val all = row.getJSONArray("members").objects()
        val members = all.filter { !it.optBoolean("deleted") }.toMutableList()
        val index = members.indexOfFirst { it.getString("positionId") == positionId }
        require(index >= 0)
        val destination = (index+delta).coerceIn(0,members.lastIndex)
        members.add(destination,members.removeAt(index));row.put("members",JSONArray(members + all.filter { it.optBoolean("deleted") }))
    }
}
