package ru.krapiva.app.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val status: Int, override val message: String) : Exception(message)
class AccountApi(private val store: AccountStore) {
    private var session: JSONObject? = null
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) { session = store.session(); session != null }
    suspend fun accept(value: JSONObject) = withContext(Dispatchers.IO) {
        store.clear(); store.saveSession(value); session = value
    }
    suspend fun clear() = withContext(Dispatchers.IO) { session = null; store.clear() }
    private fun raw(path: String, method: String, body: ByteArray?, contentType: String, token: String?): ByteArray {
        // Android's HttpURLConnection adds form Content-Type to bodyless DELETE.
        // Send valid empty JSON so Fastify's JSON-only API can parse the request.
        val payload = body ?: if (method == "DELETE") "{}".toByteArray(Charsets.UTF_8) else null
        val connection = URL(BuildConfig.API_BASE_URL + path.removePrefix("/")).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
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
    suspend fun bytes(path: String, method: String = "GET", body: ByteArray? = null, contentType: String = "application/json", secure: Boolean = true): ByteArray = withContext(Dispatchers.IO) {
        if (secure && session == null) throw ApiFailure(401, "Требуется вход")
        try { raw(path, method, body, contentType, if (secure) session?.getString("accessToken") else null) }
        catch (error: ApiFailure) {
            if (error.status != 401 || !secure) throw error
            val refresh = session?.getString("refreshToken") ?: throw error
            val response = raw("v1/auth/refresh", "POST", JSONObject().put("refreshToken", refresh).toString().toByteArray(), "application/json", null)
            val next = JSONObject(String(response, Charsets.UTF_8))
            store.saveSession(next); session = next
            raw(path, method, body, contentType, next.getString("accessToken"))
        }
    }
    suspend fun json(path: String, method: String = "GET", body: JSONObject? = null, secure: Boolean = true): JSONObject {
        val data = bytes(path, method, body?.toString()?.toByteArray(Charsets.UTF_8), secure = secure)
        return if (data.isEmpty()) JSONObject() else JSONObject(String(data, Charsets.UTF_8))
    }
    suspend fun cached(): JSONObject? = withContext(Dispatchers.IO) { session?.let { store.cached(it.getString("userId")) } }
    suspend fun cache(profile: JSONObject) = withContext(Dispatchers.IO) { store.cache(profile.getString("userId"), profile) }
    suspend fun photo(mediaId: String, offline: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        val owner = session?.getString("userId") ?: return@withContext null
        if (offline) return@withContext store.photo(owner, mediaId)
        try { bytes("v1/media/$mediaId?size=thumbnail").also { store.cachePhoto(owner, mediaId, it) } }
        catch (error: java.io.IOException) { store.photo(owner, mediaId) ?: throw error }
    }
}
