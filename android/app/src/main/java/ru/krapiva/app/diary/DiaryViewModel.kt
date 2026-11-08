package ru.krapiva.app.diary

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.catalog.text
import java.util.UUID
import java.time.Instant

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
enum class DiaryPage { HOME, DIARY, UNIQUE, WISHLIST, STATISTICS, DETAIL, EDITOR, PICK, DRAFTS }
data class DiaryFilters(val q: String = "", val styleId: String = "",val breweryId: String = "",val from: String = "",val to: String = "",val ratingMin: String = "",val ratingMax: String = "",val unrated: Boolean = false,val photo: String = "",val venueId: String = "",val venue: String = "",val sort: String = "date_desc") {
    fun query(page: DiaryPage,cursor: String? = null): String {
        val params = linkedMapOf("limit" to "20", "q" to q, "styleId" to styleId,"breweryId" to breweryId)
        if (page == DiaryPage.DIARY) params.putAll(mapOf("from" to DiaryDates.boundary(from,false),"to" to DiaryDates.boundary(to,true),"ratingMin" to (DiaryNumbers.quarters(ratingMin)?.toString() ?: ""),"ratingMax" to (DiaryNumbers.quarters(ratingMax)?.toString() ?: ""),"unrated" to if(unrated) "true" else "","hasPhoto" to photo,"venueId" to venueId,"venue" to venue,"sort" to sort))
        else params["sort"] = if (sort in setOf("name","latest","rating")) sort else "name"
        cursor?.let { params["cursor"] = it }
        return params.filterValues { it.isNotBlank() }.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
    }
}
data class DiaryState(val page: DiaryPage = DiaryPage.HOME,val items: List<JSONObject> = emptyList(),val next: String? = null,val data: JSONObject? = null,val statistics: JSONObject? = null,val tags: List<JSONObject> = emptyList(),val filters: DiaryFilters = DiaryFilters(),val busy: Boolean = false,val offline: Boolean = false,val error: String? = null,val notice: String? = null,val editor: JSONObject? = null,val pickerQuery: String = "")
class DiaryViewModel(val api: AccountApi,private val upload: suspend (Uri) -> String,private val expired: () -> Unit): ViewModel() {
    private val mutable = MutableStateFlow(DiaryState())
    val state = mutable.asStateFlow()
    private var lastRequest = -1
    private val uploads = mutableMapOf<String,String>()
    private fun task(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true,error = null,notice = null)
        viewModelScope.launch { try { block() } catch(e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiFailure && e.status == 401) expired()
            mutable.value = mutable.value.copy(error = when(e) {
                is ApiFailure -> if(e.status == 409) "Конфликт версии или повторного запроса. Обновите запись; локальный черновик сохранён." else e.message
                is java.io.IOException -> "Нет связи. Чекин не подтверждён сервером. Сохраните черновик и повторите позже."
                is SecurityException -> "Нет доступа к выбранному фото. Выберите его ещё раз."
                is IllegalArgumentException -> e.message ?: "Проверьте поля"
                else -> "Не удалось выполнить действие. Повторите попытку."
            })
        } finally { mutable.value = mutable.value.copy(busy = false) } }
    }
    fun entry(token: Int,page: String,position: JSONObject?) {
        if(token == lastRequest) return
        lastRequest = token
        if(page.startsWith("detail:")) detail(page.removePrefix("detail:")) else if(page == "new") new(position) else open(runCatching { DiaryPage.valueOf(page) }.getOrDefault(DiaryPage.HOME))
    }
    fun filters(value: DiaryFilters) { if(!mutable.value.busy) mutable.value = mutable.value.copy(filters = value) }
    fun pickerQuery(value: String) { mutable.value = mutable.value.copy(pickerQuery = value.take(120)) }
    fun open(page: DiaryPage,filters: DiaryFilters = DiaryFilters()) {
        if(mutable.value.busy) return
        mutable.value = DiaryState(page = page,filters = filters); refresh()
    }
    fun refresh(more: Boolean = false) = task { load(more) }
    private suspend fun load(more: Boolean = false) {
        val old = mutable.value
        if(old.page == DiaryPage.DRAFTS) { mutable.value = old.copy(items = api.drafts().objects()); return }
        val path = when(old.page) {
            DiaryPage.HOME -> "v1/me/home"
            DiaryPage.DIARY -> "v1/checkins?${old.filters.query(old.page,if(more)old.next else null)}"
            DiaryPage.UNIQUE -> "v1/me/unique-positions?${old.filters.query(old.page,if(more)old.next else null)}"
            DiaryPage.WISHLIST -> "v1/me/wishlist?${old.filters.query(old.page,if(more)old.next else null)}"
            DiaryPage.STATISTICS -> "v1/me/statistics"
            DiaryPage.DETAIL -> "v1/checkins/${old.data!!.getString("id")}"
            DiaryPage.PICK -> "v1/positions?q=${Uri.encode(old.pickerQuery)}&limit=20" + (if(more)old.next?.let { "&cursor=${Uri.encode(it)}" } ?: "" else "")
            DiaryPage.EDITOR, DiaryPage.DRAFTS -> return
        }
        val (data,offline) = api.catalogGet(path)
        when(old.page) {
            DiaryPage.HOME -> mutable.value = mutable.value.copy(statistics = data.getJSONObject("statistics"),items = data.getJSONObject("recent").getJSONArray("items").objects(),offline = offline)
            DiaryPage.STATISTICS -> mutable.value = mutable.value.copy(statistics = data,offline = offline)
            DiaryPage.DETAIL -> mutable.value = mutable.value.copy(data = data,offline = offline)
            else -> mutable.value = mutable.value.copy(items = ((if(more)old.items else emptyList()) + data.getJSONArray("items").objects()).distinctBy { it.optString("id") },next = data.text("nextCursor"),offline = offline)
        }
    }
    fun detail(id: String) = task {
        val (data,offline) = api.catalogGet("v1/checkins/$id")
        mutable.value = DiaryState(page = DiaryPage.DETAIL,data = data,offline = offline)
    }
    private fun blank(position: JSONObject? = null) = JSONObject().put("draftId",UUID.randomUUID().toString()).put("requestId",UUID.randomUUID().toString()).put("positionId",position?.optString("canonicalId",position.optString("id"))).put("positionName",position?.optString("name")).put("body","").put("ratingQuarters",JSONObject.NULL).put("occurredAt",Instant.now().toString()).put("visibility","private").put("tagIds",JSONArray()).put("mediaIds",JSONArray()).put("localPhotos",JSONArray())
    fun new(position: JSONObject? = null) {
        if(mutable.value.busy) return
        mutable.value = DiaryState(page = if(position == null)DiaryPage.PICK else DiaryPage.EDITOR,editor = blank(position)); if(position == null) refresh() else tags()
    }
    fun select(position: JSONObject) {
        mutable.value = mutable.value.copy(page = DiaryPage.EDITOR,editor = blank(position),items = emptyList()); tags()
    }
    private fun tags() = task {
        val (data,_) = api.catalogGet("v1/taste-tags"); mutable.value = mutable.value.copy(tags = data.getJSONArray("items").objects())
    }
    fun edit(data: JSONObject) {
        if(mutable.value.busy) return
        val value = JSONObject(data.toString()).put("draftId",data.optString("draftId",UUID.randomUUID().toString())).put("localPhotos",data.optJSONArray("localPhotos") ?: JSONArray())
        mutable.value = DiaryState(page = DiaryPage.EDITOR,editor = value); tags()
    }
    fun saveDraft(data: JSONObject) = task {
        api.saveDraft(data); mutable.value = mutable.value.copy(notice = "Черновик сохранён на устройстве · не отправлено")
    }
    fun removeDraft(id: String) = task { api.removeDraft(id); load() }
    fun publish(data: JSONObject) = task {
        api.saveDraft(data)
        val id = data.text("id")
        // Recover an acknowledged-or-lost create before applying an edited payload with its existing UUID.
        if(id == null) {
            val previous = api.json("v1/me/checkin-requests/${data.getString("requestId")}").text("checkinId")
            if(previous != null) {
                val row = api.json("v1/checkins/$previous"); api.invalidateDiary()
                mutable.value = DiaryState(page = DiaryPage.DETAIL,data = row,notice = "Этот запрос уже сохранён. Локальная копия оставлена в черновиках для сверки.")
                return@task
            }
        }
        val photos = data.optJSONArray("localPhotos") ?: JSONArray()
        val media = data.optJSONArray("mediaIds")?.strings()?.toMutableList() ?: mutableListOf()
        for(uri in photos.strings()) media.add(uploads[uri] ?: upload(uri.toUri()).also { uploads[uri] = it })
        require(media.size <= 4) { "Не более четырёх фото" }
        val body = JSONObject()
        for(key in listOf("ratingQuarters","body","occurredAt","visibility","servingFormat","venueId","venueText","tagIds")) if(data.has(key)) body.put(key,data.get(key))
        body.put("mediaIds",JSONArray(media))
        if(id == null) body.put("positionId",data.getString("positionId")) else body.put("version",data.getInt("version"))
        val result = api.json(if(id == null)"v1/checkins" else "v1/checkins/$id",if(id == null)"POST" else "PATCH",body,requestId = if(id == null)data.getString("requestId") else null)
        api.invalidateDiary(); api.removeDraft(data.getString("draftId")); uploads.clear()
        mutable.value = DiaryState(page = DiaryPage.DETAIL,data = result,notice = "Чекин сохранён на сервере")
    }
    fun delete() = task {
        val data = mutable.value.data ?: return@task
        api.json("v1/checkins/${data.getString("id")}","DELETE",JSONObject().put("version",data.getInt("version")))
        api.invalidateDiary(); mutable.value = DiaryState(page = DiaryPage.DIARY,busy = true,notice = "Чекин удалён"); load()
    }
}
