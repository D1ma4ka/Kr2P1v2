package ru.krapiva.app.account

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException

enum class AccountScreen { RESTORING, WELCOME, LOGIN, REGISTER, VERIFY, FORGOT, RESET, PROFILE, EDIT, SETTINGS, CATALOG, DIARY, SOCIAL, HOME, VENUES, BADGES }
data class AccountState(
    val authEmail: String = "", val screen: AccountScreen = AccountScreen.RESTORING, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null, val profile: JSONObject? = null,
    val avatar: Bitmap? = null, val cover: Bitmap? = null, val offline: Boolean = false, val fullPhoto: Bitmap? = null, val catalogPosition: String? = null,
)
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AccountStore(application)
    val api = AccountApi(store)
    // Retained with the account across Activity recreation, cleared on every session change.
    val catalogOwner = object : androidx.lifecycle.ViewModelStoreOwner {
        override val viewModelStore = androidx.lifecycle.ViewModelStore()
    }
    var socialRequest by mutableIntStateOf(0); private set
    var socialCheckinId: String? = null; private set
    var socialPage = ru.krapiva.app.social.SocialPage.FEED; private set
    fun openFriends() { if (!mutable.value.busy) { socialRequest++; socialCheckinId = null; socialPage = ru.krapiva.app.social.SocialPage.FRIENDS; navigate(AccountScreen.SOCIAL) } }
    fun openHome() = navigate(AccountScreen.HOME)
    fun openOwnCheckin(id: String) = openDiary("detail:$id")
    fun openSocial(id: String? = null) { if (!mutable.value.busy) { socialRequest++; socialCheckinId = id; socialPage = ru.krapiva.app.social.SocialPage.FEED; navigate(AccountScreen.SOCIAL) } }
    var placesRequest=0;private set
    var venueId: String? = null; private set
    var placesReturn = AccountScreen.HOME; private set
    var diaryVenue: JSONObject? = null; private set
    fun openVenues(id: String? = null) { placesRequest++;placesReturn=mutable.value.screen;venueId=id;navigate(AccountScreen.VENUES) }
    fun checkinAt(venue: JSONObject) { openDiary("new",venue=venue) }
    var diaryRequest = 0; private set
    var diaryEntry = "HOME"; private set
    var diaryPosition: JSONObject? = null; private set
    val catalogPosition: String? get() = mutable.value.catalogPosition
    fun openDiary(entry: String = "HOME",position: JSONObject? = null,venue: JSONObject? = null) {
        if (mutable.value.busy) return
        diaryRequest++; diaryEntry = entry; diaryPosition = position; diaryVenue=venue; navigate(AccountScreen.DIARY)
    }
    fun openPosition(id: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(screen = AccountScreen.CATALOG,catalogPosition = id,error = null,notice = null) }
    fun consumeCatalogPosition() { mutable.value = mutable.value.copy(catalogPosition = null) }
    private suspend fun clearSession(preserveDrafts: Boolean = true) { catalogOwner.viewModelStore.clear(); diaryPosition = null; diaryVenue=null;venueId=null;socialCheckinId = null; consumeCatalogPosition(); api.clear(preserveDrafts) }
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    init { restore() }
    fun navigate(screen: AccountScreen, authEmail: String = mutable.value.authEmail) { if (!mutable.value.busy) mutable.value = mutable.value.copy(screen = screen, authEmail = authEmail, error = null, notice = null) }
    private fun task(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (error is ApiFailure && error.status == 401 && mutable.value.profile != null) {
                    clearSession(); mutable.value = AccountState(screen = AccountScreen.LOGIN, error = "Сессия завершена. Войдите снова.")
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
            if (api.restore()) loadProfile(AccountScreen.HOME) else mutable.value = AccountState(screen = AccountScreen.WELCOME)
        } catch (error: ApiFailure) {
            if (error.status == 401) { clearSession(); mutable.value = AccountState(screen = AccountScreen.LOGIN) } else throw error
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // A truncated/invalid encrypted session must not strand the app on RESTORING.
            clearSession()
            mutable.value = AccountState(screen = AccountScreen.WELCOME, error = "Сохранённая сессия недоступна. Войдите снова.")
        }
    }
    private suspend fun loadProfile(destination: AccountScreen = AccountScreen.PROFILE) {
        val profile = try { api.json("v1/me") } catch (error: Exception) {
            if (error !is IOException && !(error is ApiFailure && error.status >= 500)) throw error
            val cached = api.cached() ?: throw error
            mutable.value = AccountState(screen = destination, profile = cached, offline = true,
                error = "Нет связи. Показана сохранённая копия профиля.")
            loadPhotos(cached)
            return
        }
        api.cache(profile)
        mutable.value = AccountState(screen = destination, profile = profile, busy = true)
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
        val result = api.json("v1/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password)
            .put("displayName", UserHandle.normalize(name)).put("handle", UserHandle.normalize(name)).put("adultConfirmed", adult), false)
        mutable.value = mutable.value.copy(authEmail = email.trim(),screen = AccountScreen.VERIFY, notice = result.getString("message") + " Введите 6 цифр из письма. Срок — 10 минут.")
    }
    fun checkHandle(handle: String) = task {
        val result = api.json("v1/auth/handles/${android.net.Uri.encode(UserHandle.normalize(handle))}", secure = false)
        mutable.value = mutable.value.copy(notice = "@${UserHandle.normalize(handle)}: " + if (result.getBoolean("available")) "свободен. Окончательная проверка — при регистрации." else "Этот никнейм уже занят. Попробуй другой")
    }
    fun claimHandle(handle: String) = task {
        val profile = api.json("v1/me/handle", "PUT", JSONObject().put("handle", UserHandle.normalize(handle)))
        api.cache(profile); mutable.value = mutable.value.copy(profile = profile, notice = "Никнейм сохранён")
    }
    fun login(email: String, password: String) = task {
        val session = api.json("v1/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password), false)
        catalogOwner.viewModelStore.clear(); api.accept(session); loadProfile(AccountScreen.HOME)
    }
    fun verify(token: String) = task {
        api.json("v1/auth/verify", "POST", JSONObject().put("token", token.trim()), false)
        mutable.value = mutable.value.copy(screen = AccountScreen.LOGIN, notice = "Почта подтверждена. Войдите в аккаунт.")
    }
    fun verifyCode(email: String,code: String) = task {
        api.json("v1/auth/verify-code", "POST", JSONObject().put("email",email.trim()).put("code",code), false)
        mutable.value = mutable.value.copy(authEmail = email.trim(),screen = AccountScreen.LOGIN, notice = "Почта подтверждена. Войдите в аккаунт.")
    }
    fun mail(email: String, reset: Boolean) = task {
        val result = api.json("v1/auth/${if (reset) "forgot-password" else "resend"}", "POST", JSONObject().put("email", email.trim()), false)
        mutable.value = mutable.value.copy(authEmail = email.trim(),screen = if (reset) AccountScreen.RESET else AccountScreen.VERIFY, notice = result.getString("message"))
    }
    fun reset(email: String, code: String, password: String) = task {
        api.json("v1/auth/reset-password-code", "POST", JSONObject().put("email", email.trim()).put("code", code).put("password", password), false)
        clearSession(); mutable.value = AccountState(screen = AccountScreen.LOGIN, authEmail = email.trim(), notice = "Пароль изменён. Войдите снова; прежние сессии отозваны.")
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
        if (type !in setOf("image/jpeg", "image/png", "image/webp")) throw ApiFailure(415, "Выберите JPEG, PNG или WebP")
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) output.write(buffer, 0, count)
                if (output.size() > 8 * 1024 * 1024) throw ApiFailure(413, "Фото должно быть меньше 8 МБ")
                count = input.read(buffer)
            }
            output.toByteArray()
        } ?: throw IOException()
        if (bytes.size > 8 * 1024 * 1024) throw ApiFailure(413, "Фото должно быть меньше 8 МБ")
        JSONObject(String(api.bytes("v1/media", "POST", bytes, type), Charsets.UTF_8)).getString("id")
    }
    fun sessionExpired() = task { clearSession(); mutable.value = AccountState(screen = AccountScreen.LOGIN, error = "Сессия завершена. Войдите снова.") }
    fun logout() = task {
        val serverFailure = try {
            api.json("v1/session", "DELETE")
            null
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            error
        }
        // Logout is a local security action even when the network is down.
        clearSession()
        mutable.value = AccountState(screen = AccountScreen.LOGIN, notice = if (serverFailure == null) {
            "Вы вышли из аккаунта. Неопубликованные черновики сохранены на этом устройстве."
        } else {
            "Вы вышли из аккаунта на этом устройстве. Черновики сохранены; сервер обновится при следующем входе."
        })
    }
    fun clearLocally() = task { clearSession(false); mutable.value = AccountState(screen = AccountScreen.LOGIN,
        notice = "Данные удалены с телефона, включая локальные черновики. Серверная сессия действует до истечения срока или сброса пароля.") }
    fun deleteAccount() = task {
        val response = api.json("v1/me/deletion-request", "POST", JSONObject())
        clearSession(); mutable.value = AccountState(screen = AccountScreen.LOGIN, notice = response.getString("message"))
    }
    override fun onCleared() { catalogOwner.viewModelStore.clear(); store.close(); super.onCleared() }
}
