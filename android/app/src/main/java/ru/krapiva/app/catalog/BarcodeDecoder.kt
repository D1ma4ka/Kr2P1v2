package ru.krapiva.app.catalog

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.UPCEReader

internal data class ScannedCode(val code: String, val kind: String)
/** Bundled decoder: all camera orientations, no network/download or arbitrary URL routing. */
internal class BarcodeDecoder {
    private val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(
        BarcodeFormat.EAN_8, BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF, BarcodeFormat.QR_CODE))) }
    fun decode(data: ByteArray, originalWidth: Int, originalHeight: Int): ScannedCode? {
        var pixels = data; var width = originalWidth; var height = originalHeight
        repeat(4) {
            try {
                val hit = reader.decodeWithState(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(pixels,width,height,0,0,width,height,false))))
                val kind = if (hit.barcodeFormat == BarcodeFormat.QR_CODE) "qr" else "gtin"
                val code = if (hit.barcodeFormat == BarcodeFormat.UPC_E) UPCEReader.convertUPCEtoUPCA(hit.text) else hit.text
                if (kind == "qr" || Gtin.normalize(code) != null) return ScannedCode(code,kind)
            } catch (_: ReaderException) { /* Try the next orientation. */ }
            finally { reader.reset() }
            pixels = rotateLuma(pixels,width,height); val oldWidth = width; width = height; height = oldWidth
        }
        return null
    }
}
