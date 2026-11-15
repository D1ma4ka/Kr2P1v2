package ru.krapiva.app.offline

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.UUID

/** Test data never uses the real application's database or session file. */
class IsolatedStoreContext(context: Context) : ContextWrapper(context) {
    private val root = File(context.cacheDir, "p84-test-${UUID.randomUUID()}").apply { mkdirs() }
    override fun getDatabasePath(name: String): File = File(root, name)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,errorHandler)
    override fun getNoBackupFilesDir(): File = root
    fun dispose() { root.deleteRecursively() }
}
