package ru.krapiva.app.catalog
import org.junit.Assert.*
import org.junit.Test
class CatalogNumbersTest {
    @Test fun russianDecimalAndNonFiniteInput() {
        assertEquals(6.5,parseFiniteNumber(" 6,5 ")!!,0.0)
        assertEquals(0.0,parseFiniteNumber("0")!!,0.0)
        assertEquals(6.5,parseFiniteNumber("\u00a06.5\u00a0")!!,0.0)
        for (value in listOf("NaN","Infinity","-Infinity","1e999","","6,5,4","неизвестно")) assertNull(value,parseFiniteNumber(value))
    }
}
