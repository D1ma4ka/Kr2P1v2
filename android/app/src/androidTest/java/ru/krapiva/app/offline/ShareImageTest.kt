package ru.krapiva.app.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ShareImageTest {
    @Test fun realPngLongTitlesAndContentUriWithoutExternalStorage() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=ShareImages.render(context,"Очень длинное название ".repeat(40),listOf("На основе доступных записей","Чекинов: 3 · позиций: 2"))
        val directory=File(context.cacheDir,"shares").apply{mkdirs()}
        val file=File(directory,"test-${UUID.randomUUID()}.png")
        try {
            assertEquals(1080,bitmap.width);assertEquals(1440,bitmap.height)
            file.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.shares",file)
            assertEquals("content",uri.scheme)
            val bytes=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()}
            assertEquals(listOf(137,80,78,71,13,10,26,10),bytes.take(8).map{it.toInt() and 255})
            val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            assertEquals(1080,decoded.width);decoded.recycle()
        } finally {file.delete();bitmap.recycle()}
        val isolated=File(context.cacheDir,"cleanup-test-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val old=File(isolated,"old.png").apply{writeText("old");setLastModified(1)}
            val fresh=File(isolated,"new.png").apply{writeText("new")}
            ShareImages.cleanup(isolated)
            assertFalse(old.exists());assertTrue(fresh.exists())
        } finally {isolated.deleteRecursively()}
    }
}
