package ru.krapiva.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.catalog.text

// All content comes from existing P5/P6 endpoints. Social state is memory-only.
data class HomeState(
    val loading: Boolean = true, val summary: JSONObject? = null, val latest: JSONObject? = null,
    val feed: JSONObject? = null, val author: JSONObject? = null, val friends: String? = null,
    val offline: Boolean = false, val error: String? = null, val socialError: String? = null, val friendsError: Boolean = false,
    val likeBusy: Boolean = false, val revision: Int = 0,
)
class HomeViewModel(private val api: AccountApi, private val expired: () -> Unit) : ViewModel() {
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    private var refreshJob: Job? = null
    private var likeJob: Job? = null
    private var generation = 0
    fun hide() {
        generation++;refreshJob?.cancel();likeJob?.cancel()
        mutable.value = mutable.value.copy(feed = null,author = null,friends = null,likeBusy = false,revision = mutable.value.revision + 1)
    }
    fun refresh() {
        hide()
        val epoch = generation
        mutable.value = mutable.value.copy(loading = true,error = null,socialError = null,friendsError = false)
        refreshJob = viewModelScope.launch {
            supervisorScope {
                val personal = async { runCatching {
                    val (home,offline) = api.catalogGet("v1/me/home")
                    if(epoch == generation) mutable.value = mutable.value.copy(summary = home.getJSONObject("statistics"),
                        latest = home.getJSONObject("recent").getJSONArray("items").optJSONObject(0),offline = offline)
                }.onFailure { fail(it,false,epoch) } }
                val social = async { runCatching {
                    val feed = api.json("v1/feed?limit=1").getJSONArray("items").optJSONObject(0)
                    if(epoch == generation) mutable.value = mutable.value.copy(feed = feed)
                    if(feed != null) {
                        val author = api.json("v1/users/${feed.getString("userId")}")
                        if(epoch == generation) mutable.value = mutable.value.copy(author = author)
                    }
                }.onFailure { fail(it,true,epoch) } }
                val friends = async { runCatching {
                    val result = api.json("v1/friends?limit=50")
                    // API has no total: explicitly show a lower bound when another page exists.
                    val count = result.getJSONArray("items").length().toString() + if(result.text("nextCursor") != null) "+" else ""
                    if(epoch == generation) mutable.value = mutable.value.copy(friends = count)
                }.onFailure {
                    if(it is CancellationException) throw it
                    if(it is ApiFailure && it.status == 401) fail(it,true,epoch)
                    else if(epoch == generation) mutable.value = mutable.value.copy(friends = null,friendsError = true)
                } }
                awaitAll(personal,social,friends)
            }
            if(epoch == generation) mutable.value = mutable.value.copy(loading = false)
        }
    }
    private fun fail(error: Throwable, social: Boolean, epoch: Int) {
        if(error is CancellationException) throw error
        if(epoch != generation) return
        if(error is ApiFailure && error.status == 401) { hide();expired();return }
        if(social) mutable.value = mutable.value.copy(feed = null,author = null,socialError = "Не удалось загрузить активность друзей. Повторите запрос.")
        else mutable.value = mutable.value.copy(summary = null,latest = null,offline = false,error = "Не удалось загрузить ваши записи. Проверьте соединение.")
    }
    fun like() {
        val post = mutable.value.feed ?: return
        if(mutable.value.likeBusy) return
        val epoch = generation
        mutable.value = mutable.value.copy(likeBusy = true)
        likeJob = viewModelScope.launch {
            try {
                val result = api.json("v1/social/checkins/${post.getString("id")}/like", if(post.getBoolean("liked")) "DELETE" else "PUT")
                if(epoch == generation) mutable.value = mutable.value.copy(feed = result)
            } catch(error: Exception) { fail(error,true,epoch) }
            finally { if(epoch == generation) mutable.value = mutable.value.copy(likeBusy = false) }
        }
    }
}
