package ru.krapiva.app.catalog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.*
import org.junit.Test
class BarcodeDecoderTest {
    @Test fun generatedEanAndQrThroughActualDecoderAtEveryOrientation() {
        for ((format,code,kind) in listOf(Triple(BarcodeFormat.EAN_13,"2900000000018","gtin"),Triple(BarcodeFormat.QR_CODE,"https://example.invalid/qr","qr"))) {
            val matrix = MultiFormatWriter().encode(code,format,480,240)
            var width = matrix.width; var height = matrix.height
            var pixels = ByteArray(width*height) { i -> if (matrix[i%width,i/width]) 0 else 255.toByte() }
            repeat(4) {
                val result = BarcodeDecoder().decode(pixels,width,height)
                assertNotNull(result); assertEquals(kind,result!!.kind)
                if (kind == "gtin") assertEquals(Gtin.normalize(code),Gtin.normalize(result.code)) else assertEquals(code,result.code)
                pixels = rotateLuma(pixels,width,height); val oldWidth = width; width = height; height = oldWidth
            }
        }
        assertNull(BarcodeDecoder().decode(ByteArray(320*240) { 255.toByte() },320,240))
    }
}
