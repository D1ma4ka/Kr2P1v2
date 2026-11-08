package ru.krapiva.app.social

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import java.util.UUID

enum class SocialPage { FEED, SEARCH, FRIENDS, REQUESTS, PROFILE, DETAIL }
data class SocialState(
    val page: SocialPage = SocialPage.FEED, val target: String? = null,
    val items: List<JSONObject> = emptyList(), val next: String? = null,
    val data: JSONObject? = null, val statistics: JSONObject? = null, val relationship: JSONObject? = null,
    val query: String = "", val busy: Boolean = false, val error: String? = null,
    val comment: String = "", val pendingComment: Boolean = false, val revision: Int = 0,
)
class SocialViewModel(val api: AccountApi, private val expired: () -> Unit): ViewModel() {
    private val mutable = MutableStateFlow(SocialState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var lastRequest: Int? = null
    fun entry(request: Int,checkin: String?,initialPage: SocialPage = SocialPage.FEED) {
        if(lastRequest == request) { refresh();return }
        lastRequest = request
        hide() // An explicit navigation supersedes a lifecycle refresh.
        open(if(checkin == null) initialPage else SocialPage.DETAIL,checkin)
    }
    private var commentId = UUID.randomUUID().toString()
    private var invitation: Pair<String,String>? = null
    private fun clearContent() { mutable.value = mutable.value.copy(items = emptyList(),next = null,data = null,statistics = null,relationship = null,revision = mutable.value.revision + 1) }
    fun hide() { generation++;job?.cancel();clearContent();mutable.value = mutable.value.copy(busy = false) }
    private fun task(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        val epoch = generation
        mutable.value = mutable.value.copy(busy = true,error = null)
        job = viewModelScope.launch {
            try { block() }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                if(epoch == generation) {
                    clearContent()
                    mutable.value = mutable.value.copy(error = when(e) {
                        is ApiFailure -> if(e.status == 404) "Запись или профиль недоступны. Возможно, изменились настройки приватности." else if(e.status == 409) "Состояние изменилось. Обновите страницу и проверьте заявку или комментарий." else e.message
                        is java.io.IOException -> "Нет связи. Социальные данные требуют сети. Действие не подтверждено — повторите запрос."
                        else -> "Не удалось загрузить данные. Повторите попытку."
                    })
                    if(e is ApiFailure && e.status == 401) expired()
                }
            } finally { if(epoch == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun open(page: SocialPage,target: String? = null) {
        if(mutable.value.busy) return
        hide();commentId = UUID.randomUUID().toString();invitation = null
        mutable.value = SocialState(page = page,target = target,revision = mutable.value.revision)
        refresh()
    }
    fun query(value: String) { if(!mutable.value.busy) mutable.value = mutable.value.copy(query = value.take(80)) }
    fun comment(value: String) { if(!mutable.value.busy && !mutable.value.pendingComment) mutable.value = mutable.value.copy(comment = value.take(2000)) }
    fun refresh(more: Boolean = false) = task { load(if(more)mutable.value.next else null) }
    private suspend fun load(cursor: String? = null) {
        val old = mutable.value
        clearContent()
        val params = "limit=20" + (cursor?.let { "&cursor=${Uri.encode(it)}" } ?: "")
        val path = when(old.page) {
            SocialPage.FEED -> "v1/feed?$params"
            SocialPage.SEARCH -> if(old.query.trim().length >= 2) "v1/users?$params&q=${Uri.encode(old.query.trim())}" else null
            SocialPage.FRIENDS -> "v1/friends?$params"
            SocialPage.REQUESTS -> "v1/friend-requests?$params"
            SocialPage.PROFILE -> "v1/users/${old.target}/checkins?$params"
            SocialPage.DETAIL -> "v1/social/checkins/${old.target}/comments?$params"
        }
        var data: JSONObject? = null;var stats: JSONObject? = null;var relationship: JSONObject? = null
        if(old.page == SocialPage.PROFILE) {
            data = api.json("v1/users/${old.target}")
            stats = api.json("v1/users/${old.target}/statistics")
            relationship = api.json("v1/users/${old.target}/relationship")
        }
        if(old.page == SocialPage.DETAIL) data = api.json("v1/social/checkins/${old.target}")
        val result = path?.let { api.json(it) }
        mutable.value = mutable.value.copy(data = data,statistics = stats,relationship = relationship,items = result?.getJSONArray("items")?.objects().orEmpty(),next = result?.text("nextCursor"))
    }
    fun invite() = task {
        val target = mutable.value.target ?: return@task
        val id = invitation?.takeIf { it.first == target }?.second ?: UUID.randomUUID().toString().also { invitation = target to it }
        api.json("v1/friend-requests/$id","PUT",JSONObject().put("userId",target));invitation = null;load()
    }
    fun decide(id: String,decision: String) = task { api.json("v1/friend-requests/$id/decision","PUT",JSONObject().put("status",decision));load() }
    fun removeFriend(id: String) = task { api.json("v1/friends/$id","DELETE");load() }
    fun like() = task {
        val row = mutable.value.data ?: return@task
        api.json("v1/social/checkins/${row.getString("id")}/like",if(row.getBoolean("liked"))"DELETE" else "PUT");load()
    }
    fun sendComment() = task {
        val old = mutable.value
        if(old.comment.isBlank()) return@task
        mutable.value = old.copy(pendingComment = true)
        api.json("v1/social/checkins/${old.target}/comments/$commentId","PUT",JSONObject().put("body",old.comment))
        commentId = UUID.randomUUID().toString()
        mutable.value = mutable.value.copy(comment = "",pendingComment = false);load()
    }
    fun removeComment(id: String) = task { api.json("v1/social/checkins/${mutable.value.target}/comments/$id","DELETE");load() }
}
