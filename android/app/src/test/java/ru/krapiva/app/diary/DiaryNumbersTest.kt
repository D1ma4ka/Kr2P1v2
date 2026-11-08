package ru.krapiva.app.diary
import org.junit.Assert.*
import org.junit.Test
class DiaryNumbersTest {
    @Test fun exactQuarterRatingsAndHalfUpDisplay() {
        assertNull(DiaryNumbers.quarters(""));assertNull(DiaryNumbers.quarters("  "))
        for(q in 1..20)assertEquals(q,DiaryNumbers.quarters(DiaryNumbers.rating(q)))
        assertEquals(17,DiaryNumbers.quarters("4,25"));assertEquals("4,42",DiaryNumbers.average((13.25/3).toString()));assertEquals("4,43",DiaryNumbers.average("4.425"));assertEquals("Нет оценок",DiaryNumbers.average(null))
        for(invalid in listOf("0","5.25","0.1","4.3","-1","NaN","Infinity","9999999999999999999999")) {
            try { DiaryNumbers.quarters(invalid);fail(invalid) } catch(_: IllegalArgumentException) { }
        }
    }
}
