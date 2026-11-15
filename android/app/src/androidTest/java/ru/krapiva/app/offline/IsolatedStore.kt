package ru.krapiva.app.offline

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.UUID

/** Test data never uses the real application's database or session file. */
class IsolatedStoreContext(context: Context) : ContextWrapper(context) {
    private val root = File(context.cacheDir, "p84-test-${UUID.randomUUID()}").apply { mkdirs() }
    override fun getDatabasePath(name: String): File = File(root, name)
    override fun getNoBackupFilesDir(): File = root
    fun dispose() { root.deleteRecursively() }
}
