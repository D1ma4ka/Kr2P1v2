package ru.krapiva.app.diary
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
class DiaryDatesTest {
    @Test fun localDayBoundsAndStrictCalendarAreConvertedToUtc() {
        val zone = ZoneId.of("Europe/Istanbul")
        assertEquals("2026-11-07 13:00",DiaryDates.display("2026-11-07T10:00:00Z",zone))
        assertEquals("2026-11-07T10:00:00Z",DiaryDates.instant("2026-11-07 13:00",zone))
        assertEquals("2026-11-06T21:00:00Z",DiaryDates.boundary("2026-11-07",false,zone))
        assertEquals("2026-11-07T20:59:59.999999Z",DiaryDates.boundary("2026-11-07",true,zone))
        assertEquals("",DiaryDates.boundary("",false,zone))
        for(value in listOf("2026-02-31 12:00","2026-11-07 24:00"))try { DiaryDates.instant(value,zone);fail(value) }catch(_: java.time.DateTimeException) { }
    }
}
