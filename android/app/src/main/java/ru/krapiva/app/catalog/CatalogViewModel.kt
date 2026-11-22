package ru.krapiva.app.catalog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.ApiFailure
import java.io.IOException

enum class CatalogPage { POSITIONS, POSITION, BREWERIES, BREWERY, STYLES, PROPOSAL, PROPOSALS, SCANNER }
data class CatalogFilters(val q: String = "", val sort: String = "newest", val breweryId: String = "", val breweryLabel: String = "",
    val styleId: String = "", val styleLabel: String = "", val packaging: String = "", val abvMin: String = "", val abvMax: String = "") {
    fun query(cursor: String? = null): String = listOf("q" to q, "sort" to sort, "breweryId" to breweryId, "styleId" to styleId,
        "packaging" to packaging, "abvMin" to abvMin, "abvMax" to abvMax, "cursor" to (cursor ?: ""), "limit" to "20")
        .filter { it.second.isNotBlank() }.joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
}
data class CatalogState(val page: CatalogPage = CatalogPage.POSITIONS, val items: List<JSONObject> = emptyList(),
    val next: String? = null, val detail: JSONObject? = null, val filters: CatalogFilters = CatalogFilters(),
    val busy: Boolean = false, val offline: Boolean = false, val error: String? = null, val notice: String? = null,
    val queries: List<String> = emptyList(),val recent: List<String> = emptyList(),
    val code: String = "", val lookupState: String? = null, val lookupSource: String? = null,
    val pickerQuery: String = "", val proposal: JSONObject? = null, val refreshOnBack: Boolean = false)
class CatalogViewModel(val repository: CatalogRepository, private val upload: suspend (Uri) -> String,
    private val expired: () -> Unit) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState())
    val state = mutable.asStateFlow()
    private val history = ArrayDeque<CatalogState>()
    private val uploads = mutableMapOf<String, String>()
    init { viewModelScope.launch {
        try { val saved = repository.history.read();mutable.value = mutable.value.copy(filters = repository.history.filters(saved),queries = saved.optJSONArray("queries")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),recent = saved.optJSONArray("recent")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()) }
        catch(e: Exception) { if(e is CancellationException)throw e;mutable.value=mutable.value.copy(error="История поиска недоступна") }
        refresh()
    } }
    fun clearHistory() = task {
        repository.history.clear()
        val cleared = history.map { previous ->
            val hadQuery = previous.page == CatalogPage.POSITIONS && previous.filters.q.isNotBlank()
            previous.copy(
                filters = previous.filters.copy(q = ""),
                items = if (hadQuery) emptyList() else previous.items,
                next = if (hadQuery) null else previous.next,
                queries = emptyList(),
                recent = emptyList(),
                refreshOnBack = previous.refreshOnBack || hadQuery,
            )
        }
        history.clear()
        history.addAll(cleared)
        mutable.value = mutable.value.copy(filters = mutable.value.filters.copy(q = ""), queries = emptyList(), recent = emptyList())
        load(more = false)
    }
    private fun task(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is ApiFailure && error.status == 401) expired()
                if (error is ApiFailure && error.status in setOf(403,404)) mutable.value = mutable.value.copy(detail = null)
                mutable.value = mutable.value.copy(error = when (error) {
                    is ApiFailure -> error.message
                    is IOException -> "Нет связи с сервером. Повторите запрос."
                    is SecurityException -> "Нет доступа к фотографии. Выберите её ещё раз."
                    else -> "Не удалось выполнить действие. Повторите попытку."
                })
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private fun enter(next: CatalogState) { if (history.size >= 32) history.removeFirst(); history.addLast(mutable.value.copy(busy = false, error = null)); mutable.value = next }
    fun back(refreshResults: Boolean = true): Boolean {
        if (mutable.value.busy) return true
        val old = history.removeLastOrNull() ?: return false
        mutable.value = old.copy(queries=mutable.value.queries,recent=mutable.value.recent,refreshOnBack=false)
        if (refreshResults && old.refreshOnBack) refresh()
        return true
    }
    fun open(page: CatalogPage) {
        if (mutable.value.busy) return
        if (page == CatalogPage.SCANNER && mutable.value.page == page) mutable.value = CatalogState(page = page, filters = mutable.value.filters)
        else enter(CatalogState(page = page, filters = mutable.value.filters,queries=mutable.value.queries,recent=mutable.value.recent))
        if (page in setOf(CatalogPage.POSITIONS, CatalogPage.BREWERIES, CatalogPage.STYLES, CatalogPage.PROPOSALS)) refresh()
    }
    fun filters(value: CatalogFilters) { if (!mutable.value.busy) mutable.value = mutable.value.copy(filters = value) }
    fun pickerQuery(value: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(pickerQuery = value.take(120)) }
    fun selectStyle(value: JSONObject) {
        if (mutable.value.busy) return
        val filters = mutable.value.filters.copy(styleId = value.getString("id"), styleLabel = value.getString("name"))
        back(refreshResults = false); mutable.value = mutable.value.copy(filters = filters); refresh()
    }
    fun selectBrewery() {
        val brewery = mutable.value.detail ?: return
        val filters = mutable.value.filters.copy(breweryId = brewery.getString("id"), breweryLabel = brewery.getString("name"))
        enter(CatalogState(filters = filters)); refresh()
    }
    fun resetFilters() { filters(CatalogFilters()); refresh() }
    fun refresh(more: Boolean = false) = task { load(more) }
    private suspend fun load(more: Boolean) {
        val old = mutable.value
        if(!more && old.page in setOf(CatalogPage.POSITIONS,CatalogPage.BREWERIES,CatalogPage.STYLES,CatalogPage.PROPOSALS))mutable.value=mutable.value.copy(items=emptyList(),next=null)
        if(old.page == CatalogPage.POSITIONS && !more) {
            repository.history.search(old.filters)
            mutable.value = mutable.value.copy(queries=recentValues(old.queries,old.filters.q))
        }
        val cursor = if (more) old.next else null
        val path = when (old.page) {
            CatalogPage.POSITIONS -> "v1/positions?${old.filters.query(cursor)}"
            CatalogPage.PROPOSALS -> "v1/me/position-proposals?limit=20" + (cursor?.let { "&cursor=${Uri.encode(it)}" } ?: "")
            CatalogPage.BREWERIES, CatalogPage.STYLES -> "v1/${if (old.page == CatalogPage.BREWERIES) "breweries" else "styles"}?q=${Uri.encode(old.pickerQuery)}&limit=20" + (cursor?.let { "&cursor=${Uri.encode(it)}" } ?: "")
            CatalogPage.POSITION -> "v1/positions/${old.detail!!.getString("requestedId")}"
            CatalogPage.BREWERY -> "v1/breweries/${old.detail!!.getString("id")}"
            else -> return
        }
        val (data, offline) = repository.read(path)
        if (old.page in setOf(CatalogPage.POSITION, CatalogPage.BREWERY)) mutable.value = mutable.value.copy(detail = data, offline = offline)
        else {
            val array = data.getJSONArray("items"); val items = (0 until array.length()).map { array.getJSONObject(it) }
            mutable.value = mutable.value.copy(items = ((if (more) old.items else emptyList()) + items).distinctBy { it.getString("id") },
                next = data.text("nextCursor"), offline = offline)
        }
    }
    fun detail(id: String, brewery: Boolean = false, standalone: Boolean = false) = task {
        if (standalone) {
            history.clear()
            mutable.value = CatalogState(page = CatalogPage.POSITION, busy = true)
        }
        val (data, offline) = repository.read("v1/${if (brewery) "breweries" else "positions"}/$id")
        if(!brewery) repository.history.viewed(id)
        val next = CatalogState(queries=mutable.value.queries,recent=if(brewery)mutable.value.recent else recentValues(mutable.value.recent,id),page = if (brewery) CatalogPage.BREWERY else CatalogPage.POSITION, detail = data, offline = offline, filters = mutable.value.filters)
        if (standalone) mutable.value = next else enter(next)
    }
    fun proposal(existing: JSONObject? = null) {
        if (mutable.value.busy) return
        enter(CatalogState(page = CatalogPage.PROPOSAL, filters = mutable.value.filters, code = mutable.value.code, proposal = existing))
    }
    fun submit(data: JSONObject, photo: String?) = task {
        if (photo != null) data.put("imageAssetId", uploads[photo] ?: upload(Uri.parse(photo)).also { uploads[photo] = it })
        val current = mutable.value.proposal
        val response = if (current == null) repository.propose(data) else repository.edit(current.getString("id"), data.put("version", current.getInt("version")))
        uploads.clear()
        mutable.value = CatalogState(page = CatalogPage.PROPOSALS, notice = "Заявка ${response.getString("id")} сохранена на сервере.")
        history.clear()
        // Fetch explicitly here to preserve the successful submission notice.
        val (page, offline) = repository.read("v1/me/position-proposals?limit=20")
        val array = page.getJSONArray("items")
        mutable.value = mutable.value.copy(items = (0 until array.length()).map { array.getJSONObject(it) }, next = page.text("nextCursor"), offline = offline)
    }
    fun lookup(code: String, kind: String = "gtin") {
        if (mutable.value.busy) return
        if (kind == "qr") {
            // QR may contain private URLs, contacts or Wi-Fi credentials. It has no product route.
            mutable.value = mutable.value.copy(code = "", lookupState = "unsupported_qr", lookupSource = null, error = null)
            return
        }
        val valid = Gtin.normalize(code)
        if (valid == null) { mutable.value = mutable.value.copy(error = "Некорректный штрихкод или контрольная цифра", lookupState = "invalid"); return }
        task {
            val result = repository.lookup(valid, kind)
            if (result.getString("state") == "found") enter(CatalogState(page = CatalogPage.POSITION, detail = result.getJSONObject("position"), filters = mutable.value.filters))
            else mutable.value = mutable.value.copy(code = if (kind == "gtin") valid else "", lookupState = result.getString("state"), lookupSource = result.text("source"))
        }
    }
}
