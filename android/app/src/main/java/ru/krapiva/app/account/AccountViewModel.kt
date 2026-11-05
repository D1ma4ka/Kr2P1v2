package ru.krapiva.app.account

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException

enum class AccountScreen { RESTORING, WELCOME, LOGIN, REGISTER, VERIFY, FORGOT, RESET, PROFILE, EDIT, SETTINGS }
data class AccountState(
    val screen: AccountScreen = AccountScreen.RESTORING, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null, val profile: JSONObject? = null,
    val avatar: Bitmap? = null, val cover: Bitmap? = null, val offline: Boolean = false, val fullPhoto: Bitmap? = null,
)
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AccountStore(application)
    private val api = AccountApi(store)
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    init { restore() }
    fun navigate(screen: AccountScreen) { if (!mutable.value.busy) mutable.value = mutable.value.copy(screen = screen, error = null, notice = null) }
    private fun task(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (error is ApiFailure && error.status == 401 && mutable.value.profile != null) {
                    api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN, error = "Сессия завершена. Войдите снова.")
                } else mutable.value = mutable.value.copy(error = when (error) {
                    is ApiFailure -> error.message
                    is SecurityException -> "Нет доступа к выбранной фотографии. Выберите её ещё раз."
                    is IOException -> "Нет связи с сервером. Проверьте сеть и повторите."
                    else -> "Не удалось выполнить действие. Повторите попытку."
                })
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun restore() = task {
        try {
            if (api.restore()) loadProfile() else mutable.value = AccountState(screen = AccountScreen.WELCOME)
        } catch (error: ApiFailure) {
            if (error.status == 401) { api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN) } else throw error
        }
    }
    private suspend fun loadProfile() {
        val profile = try { api.json("v1/me") } catch (error: Exception) {
            if (error !is IOException && !(error is ApiFailure && error.status >= 500)) throw error
            val cached = api.cached() ?: throw error
            mutable.value = AccountState(screen = AccountScreen.PROFILE, profile = cached, offline = true,
                error = "Нет связи. Показана сохранённая копия профиля.")
            loadPhotos(cached)
            return
        }
        api.cache(profile)
        mutable.value = AccountState(screen = AccountScreen.PROFILE, profile = profile, busy = true)
        loadPhotos(profile)
    }
    private suspend fun loadPhotos(profile: JSONObject) {
        suspend fun image(field: String): Bitmap? {
            if (profile.isNull(field)) return null
            return try {
            val bytes = api.photo(profile.getString(field), mutable.value.offline) ?: return null
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException || error is ApiFailure && error.status == 401) throw error
                mutable.value = mutable.value.copy(error = "Не удалось загрузить фото. Нажмите «Обновить», чтобы повторить.")
                null
            }
        }
        val avatar = image("avatarAssetId"); val cover = image("coverAssetId")
        mutable.value = mutable.value.copy(avatar = avatar, cover = cover)
    }
    fun refresh() = task { loadProfile() }
    fun register(email: String, password: String, name: String, adult: Boolean) = task {
        api.json("v1/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password)
            .put("displayName", name.trim()).put("adultConfirmed", adult), false)
        mutable.value = mutable.value.copy(screen = AccountScreen.VERIFY, notice = "Проверьте почту и вставьте одноразовый код. Срок — 30 минут.")
    }
    fun login(email: String, password: String) = task {
        val session = api.json("v1/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password), false)
        api.accept(session); loadProfile()
    }
    fun verify(token: String) = task {
        api.json("v1/auth/verify", "POST", JSONObject().put("token", token.trim()), false)
        mutable.value = mutable.value.copy(screen = AccountScreen.LOGIN, notice = "Почта подтверждена. Войдите в аккаунт.")
    }
    fun mail(email: String, reset: Boolean) = task {
        val result = api.json("v1/auth/${if (reset) "forgot-password" else "resend"}", "POST", JSONObject().put("email", email.trim()), false)
        mutable.value = mutable.value.copy(screen = if (reset) AccountScreen.RESET else AccountScreen.VERIFY, notice = result.getString("message"))
    }
    fun reset(token: String, password: String) = task {
        api.json("v1/auth/reset-password", "POST", JSONObject().put("token", token.trim()).put("password", password), false)
        api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN, notice = "Пароль изменён. Войдите снова; прежние сессии отозваны.")
    }
    fun save(profile: JSONObject, avatar: Uri? = null, cover: Uri? = null) = task {
        val oldPhotos = listOfNotNull(mutable.value.profile?.optString("avatarAssetId"), mutable.value.profile?.optString("coverAssetId")).filter { it.isNotBlank() && it != "null" }.toSet()
        val uploaded = mutableListOf<String>()
        try {
            if (avatar != null) profile.put("avatarAssetId", upload(avatar).also { uploaded.add(it) })
            if (cover != null) profile.put("coverAssetId", upload(cover).also { uploaded.add(it) })
        val result = api.json("v1/me/profile", "PATCH", profile)
        api.cache(result); mutable.value = mutable.value.copy(screen = AccountScreen.PROFILE, profile = result, offline = false, notice = "Профиль сохранён на сервере")
        loadPhotos(result)
        val kept = setOf(result.optString("avatarAssetId"), result.optString("coverAssetId"))
        for (id in oldPhotos - kept) runCatching { api.json("v1/media/$id", "DELETE") }
        } catch (error: Exception) {
            for (id in uploaded) runCatching { api.json("v1/media/$id", "DELETE") }
            throw error
        }
    }
    fun viewPhoto(field: String) = task {
        val id = mutable.value.profile?.optString(field)?.takeIf { it.isNotBlank() && it != "null" } ?: return@task
        val bytes = if (mutable.value.offline) api.photo(id, true) else api.bytes("v1/media/$id")
        if (bytes != null) mutable.value = mutable.value.copy(fullPhoto = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) })
    }
    fun closePhoto() { mutable.value = mutable.value.copy(fullPhoto = null) }
    // Photo edits are staged in the form; only Save attaches the uploaded asset.
    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val type = resolver.getType(uri) ?: "image/jpeg"
        if (type !in setOf("image/jpeg", "image/png", "image/webp")) throw IOException("Unsupported image")
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                output.write(buffer, 0, count)
                if (output.size() > 8 * 1024 * 1024) throw ApiFailure(413, "Фото должно быть меньше 8 МБ")
                count = input.read(buffer)
            }
            output.toByteArray()
        } ?: throw IOException()
        if (bytes.size > 8 * 1024 * 1024) throw ApiFailure(413, "Фото должно быть меньше 8 МБ")
        JSONObject(String(api.bytes("v1/media", "POST", bytes, type), Charsets.UTF_8)).getString("id")
    }
    fun logout() = task {
        api.json("v1/session", "DELETE")
        api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN, notice = "Вы вышли из аккаунта")
    }
    fun clearLocally() = task { api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN,
        notice = "Данные удалены с телефона. Серверная сессия действует до истечения срока или сброса пароля.") }
    fun deleteAccount() = task {
        val response = api.json("v1/me/deletion-request", "POST", JSONObject())
        api.clear(); mutable.value = AccountState(screen = AccountScreen.LOGIN, notice = response.getString("message"))
    }
    override fun onCleared() { store.close(); super.onCleared() }
}
