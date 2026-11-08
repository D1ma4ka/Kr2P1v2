package ru.krapiva.app.catalog
import org.junit.Assert.*
import org.junit.Test
class GtinTest {
    @Test fun portraitCameraRotation() {
        assertArrayEquals(byteArrayOf(4,1,5,2,6,3), rotateLuma(byteArrayOf(1,2,3,4,5,6),3,2))
        var bytes = byteArrayOf(1,2,3,4,5,6)
        var width = 3; var height = 2
        repeat(4) { bytes = rotateLuma(bytes,width,height); val old = width; width = height; height = old }
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6),bytes)
    }
    @Test fun checksumAndCanonicalPadding() {
        assertEquals("00012345600012", Gtin.normalize("012345600012"))
        assertEquals("00012345600012", Gtin.normalize("00012345600012"))
        assertEquals("04006381333931", Gtin.normalize("4006381333931"))
        assertEquals("00000096385074", Gtin.normalize("96385074"))
        for (value in listOf("4006381333932", "https://example.invalid", "1234567", "１２３４５６７８")) assertNull(Gtin.normalize(value))
    }
}
