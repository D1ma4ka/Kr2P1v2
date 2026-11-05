package ru.krapiva.app.account

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens never enter preferences, backups, logs or saved instance state. */
class AccountStore(context: Context) : SQLiteOpenHelper(context, "account-cache.db", null, 1) {
    private val sessionFile = AtomicFile(File(context.noBackupFilesDir, "session.enc"))
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("krapiva-session-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder("krapiva-session-v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(data: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    }
    private fun decrypt(bytes: ByteArray): String {
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun session(): JSONObject? = if (!sessionFile.baseFile.exists()) null else JSONObject(decrypt(sessionFile.readFully()))
    fun saveSession(session: JSONObject) {
        val stream = sessionFile.startWrite()
        try { stream.write(encrypt(session.toString())); sessionFile.finishWrite(stream) }
        catch (error: Exception) { sessionFile.failWrite(stream); throw error }
    }
    override fun onConfigure(db: SQLiteDatabase) { db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() } }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profile(user_id TEXT PRIMARY KEY, payload BLOB NOT NULL)")
        db.execSQL("CREATE TABLE photos(user_id TEXT NOT NULL, media_id TEXT NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(user_id,media_id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun cache(userId: String, profile: JSONObject) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO profile(user_id,payload) VALUES(?,?)", arrayOf(userId, encrypt(profile.toString())))
    }
    fun cached(userId: String): JSONObject? = readableDatabase.rawQuery("SELECT payload FROM profile WHERE user_id=?", arrayOf(userId)).use {
        if (it.moveToFirst()) JSONObject(decrypt(it.getBlob(0))) else null
    }
    fun clear() {
        sessionFile.delete()
        writableDatabase.delete("profile", null, null)
        writableDatabase.delete("photos", null, null)
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
    }
    fun cachePhoto(userId: String, mediaId: String, bytes: ByteArray) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO photos(user_id,media_id,payload) VALUES(?,?,?)", arrayOf(userId, mediaId, encrypt(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))))
    }
    fun photo(userId: String, mediaId: String): ByteArray? = readableDatabase.rawQuery("SELECT payload FROM photos WHERE user_id=? AND media_id=?", arrayOf(userId, mediaId)).use {
        if (it.moveToFirst()) android.util.Base64.decode(decrypt(it.getBlob(0)), android.util.Base64.NO_WRAP) else null
    }
}
