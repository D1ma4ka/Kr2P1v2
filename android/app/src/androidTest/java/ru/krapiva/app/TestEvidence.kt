package ru.krapiva.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Synthetic screenshots survive AGP uninstall; no storage permission in the app. */
object TestEvidence {
    fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(!UiDevice.getInstance(instrumentation).hasObject(By.res("android", "aerr_close"))) {
            "An Android crash/ANR dialog obscures the screenshot; visual acceptance fails"
        }
        val context = instrumentation.targetContext
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KraPivaP3")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            File(context.getExternalFilesDir(null), name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
