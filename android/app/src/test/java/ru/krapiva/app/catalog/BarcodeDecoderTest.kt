package ru.krapiva.app.catalog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.*
import org.junit.Test
class BarcodeDecoderTest {
    @Test fun generatedEanUpcGtinAndQrThroughActualDecoderAtEveryOrientation() {
        for ((format,code,kind) in listOf(Triple(BarcodeFormat.EAN_13,"2900000000018","gtin"),Triple(BarcodeFormat.EAN_8,"96385074","gtin"),Triple(BarcodeFormat.UPC_A,"012345600012","gtin"),Triple(BarcodeFormat.UPC_E,"01234565","gtin"),Triple(BarcodeFormat.ITF,"00012345600012","gtin"),Triple(BarcodeFormat.QR_CODE,"https://example.invalid/qr","qr"))) {
            // UPC-E/ITF need a full quiet zone; the writer default margin is too narrow.
            val matrix = MultiFormatWriter().encode(code,format,480,240,mapOf(com.google.zxing.EncodeHintType.MARGIN to 20))
            var width = matrix.width; var height = matrix.height
            var pixels = ByteArray(width*height) { i -> if (matrix[i%width,i/width]) 0 else 255.toByte() }
            repeat(4) {
                val result = BarcodeDecoder().decode(pixels,width,height)
                assertNotNull("$format rotation $it",result); assertEquals(kind,result!!.kind)
                if (kind == "gtin") assertEquals(Gtin.normalize(if (format == BarcodeFormat.UPC_E) com.google.zxing.oned.UPCEReader.convertUPCEtoUPCA(code) else code),Gtin.normalize(result.code)) else assertEquals(code,result.code)
                pixels = rotateLuma(pixels,width,height); val oldWidth = width; width = height; height = oldWidth
            }
        }
        val internalCode = MultiFormatWriter().encode("96385074",BarcodeFormat.ITF,480,240,mapOf(com.google.zxing.EncodeHintType.MARGIN to 20))
        val internalPixels = ByteArray(internalCode.width*internalCode.height) { i -> if (internalCode[i%internalCode.width,i/internalCode.width]) 0 else 255.toByte() }
        assertNull("ITF-8 must not be mistaken for an EAN-8 product",BarcodeDecoder().decode(internalPixels,internalCode.width,internalCode.height))
        assertNull(BarcodeDecoder().decode(ByteArray(320*240) { 255.toByte() },320,240))
    }
}
