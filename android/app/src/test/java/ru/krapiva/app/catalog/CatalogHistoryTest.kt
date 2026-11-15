package ru.krapiva.app.catalog
import org.junit.Assert.*
import org.junit.Test
class CatalogHistoryTest {
    @Test fun recentQueriesAreTrimmedBoundedAndDeduplicated() {
        assertEquals(listOf("IPA","стаут"),recentValues(listOf("ipa","стаут")," IPA "))
        assertEquals(listOf("a"),recentValues(listOf("a"),"  "))
        assertEquals(20,recentValues((1..30).map(Int::toString),"new").size)
    }
}
