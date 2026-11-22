package ru.krapiva.app.account

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val status: Int, override val message: String) : Exception(message)
class AccountApi(private val store: AccountStore) {
    private val refreshMutex = Mutex()
    private val localMutex = Mutex()
    fun ownerId(): String? = session?.optString("userId")
    @Volatile private var session: JSONObject? = null
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) { session = store.session(); session != null }
    suspend fun accept(value: JSONObject) = withContext(Dispatchers.IO) {
        store.clear(preserveDrafts = true); store.saveSession(value); session = value
    }
    suspend fun clear(preserveDrafts: Boolean = false) = withContext(Dispatchers.IO) { session = null; store.clear(preserveDrafts) }
    private fun raw(path: String, method: String, body: ByteArray?, contentType: String, token: String?, requestId: String? = null): ByteArray {
        // Android's HttpURLConnection may add form Content-Type to bodyless PUT/DELETE.
        // Send valid empty JSON so Fastify's JSON-only API can parse the request.
        val payload = body ?: if (method in setOf("PUT", "DELETE")) "{}".toByteArray(Charsets.UTF_8) else null
        val connection = URL(BuildConfig.API_BASE_URL + path.removePrefix("/")).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            if (requestId != null) connection.setRequestProperty("Idempotency-Key", requestId)
            connection.setRequestProperty("Accept", "application/json, image/jpeg")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: byteArrayOf()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(String(bytes)).getJSONObject("error").getString("message") }.getOrDefault("Сервис временно недоступен")
                throw ApiFailure(status, message)
            }
            return bytes
        } finally { connection.disconnect() }
    }
    /** Retry only idempotent reads; writes remain single-shot to avoid duplicates. */
    private suspend fun rawWithRetry(path: String, method: String, body: ByteArray?, contentType: String, token: String?, requestId: String? = null): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return raw(path, method, body, contentType, token, requestId)
            } catch (error: java.io.IOException) {
                if (method !in setOf("GET", "HEAD") || attempt >= 2) throw error
                delay(250L shl attempt)
                attempt++
            } catch (error: ApiFailure) {
                if (method !in setOf("GET", "HEAD") || error.status !in 502..504 || attempt >= 2) throw error
                delay(250L shl attempt)
                attempt++
            }
        }
    }
    suspend fun bytes(path: String, method: String = "GET", body: ByteArray? = null, contentType: String = "application/json", secure: Boolean = true, requestId: String? = null): ByteArray = withContext(Dispatchers.IO) {
        if (secure && session == null) throw ApiFailure(401, "Требуется вход")
        val access = if (secure) session?.getString("accessToken") else null
        try { rawWithRetry(path, method, body, contentType, access, requestId) }
        catch (error: ApiFailure) {
            if (error.status != 401 || !secure) throw error
            val token = refreshMutex.withLock {
                val current = session ?: throw error
                if (current.getString("accessToken") != access) current.getString("accessToken") else {
                    val response = raw("v1/auth/refresh", "POST", JSONObject().put("refreshToken", current.getString("refreshToken")).toString().toByteArray(), "application/json", null)
                    val next = JSONObject(String(response, Charsets.UTF_8))
                    store.saveSession(next); session = next
                    next.getString("accessToken")
                }
            }
            rawWithRetry(path, method, body, contentType, token, requestId)
        }
    }
    suspend fun json(path: String, method: String = "GET", body: JSONObject? = null, secure: Boolean = true, requestId: String? = null): JSONObject {
        val data = bytes(path, method, body?.toString()?.toByteArray(Charsets.UTF_8), secure = secure, requestId = requestId)
        return if (data.isEmpty()) JSONObject() else JSONObject(String(data, Charsets.UTF_8))
    }
    suspend fun catalogGet(path: String): Pair<JSONObject, Boolean> = withContext(Dispatchers.IO) {
        val user = session?.getString("userId") ?: throw ApiFailure(401, "Требуется вход")
        try { val data = json(path); store.cacheCatalog(user, path, data); data to false }
        catch (error: Exception) {
            if (error is ApiFailure && error.status in setOf(403, 404)) store.evictCatalog(user,path)
            if (error !is java.io.IOException && !(error is ApiFailure && error.status >= 500)) throw error
            val cached = store.cachedCatalog(user,path) ?: throw error
            cached to true
        }
    }
    suspend fun localDocument(name: String, expectedOwner: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val user = ownerId() ?: throw ApiFailure(401,"Требуется вход")
        check(expectedOwner == null || expectedOwner == user) { "Аккаунт изменился" }
        store.localDocument(user,name) ?: JSONObject()
    }
    suspend fun editLocalDocument(name: String, expectedOwner: String? = null, edit: (JSONObject) -> Unit): JSONObject = localMutex.withLock {
        withContext(Dispatchers.IO) {
            val user = ownerId() ?: throw ApiFailure(401,"Требуется вход")
            check(expectedOwner == null || expectedOwner == user) { "Аккаунт изменился" }
            val data = store.localDocument(user,name) ?: JSONObject()
            edit(data)
            store.saveLocalDocument(user,name,data)
            data
        }
    }
    suspend fun availableCache(): List<Pair<String,JSONObject>> = withContext(Dispatchers.IO) {
        ownerId()?.let { store.availableCache(it) }.orEmpty()
    }
    suspend fun rememberBadges(owner: String?, data: JSONObject) = withContext(Dispatchers.IO) {
        if(owner != null && ownerId() == owner)store.cacheCatalog(owner,"v1/me/badges",data)
        Unit
    }
    suspend fun invalidateDiary() = withContext(Dispatchers.IO) { session?.getString("userId")?.let(store::invalidateDiary); Unit }
    suspend fun drafts(): org.json.JSONArray = withContext(Dispatchers.IO) { session?.getString("userId")?.let(store::drafts) ?: org.json.JSONArray() }
    suspend fun saveDraft(data: JSONObject) = withContext(Dispatchers.IO) { session?.getString("userId")?.let { store.saveDraft(it,data.getString("draftId"),data) }; Unit }
    suspend fun removeDraft(id: String) = withContext(Dispatchers.IO) { session?.getString("userId")?.let { store.removeDraft(it,id) }; Unit }
    suspend fun cached(): JSONObject? = withContext(Dispatchers.IO) { session?.let { store.cached(it.getString("userId")) } }
    suspend fun cache(profile: JSONObject) = withContext(Dispatchers.IO) { store.cache(profile.getString("userId"), profile) }
    suspend fun photo(mediaId: String, offline: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        val owner = session?.getString("userId") ?: return@withContext null
        if (offline) return@withContext store.photo(owner, mediaId)
        try { bytes("v1/media/$mediaId?size=thumbnail").also { store.cachePhoto(owner, mediaId, it) } }
        catch (error: java.io.IOException) { store.photo(owner, mediaId) ?: throw error }
    }
}
