package ru.krapiva.app.offline
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
class PersonalAnalyticsTest {
    private fun row(id:String,position:String,q:Int?,time:String="2025-12-31T22:30:00Z",version:Long=1,style:String?=null)=PersonalCheckin(id,position,position,"Brewery",style,q,Instant.parse(time),version,listOf("Хмель"))
    @Test fun latestVersionNullRatingAndUniquePackagesAreHandled() {
        val s=PersonalAnalytics.summarize(listOf(row("1","can",20),row("1","can",null,version=2),row("2","bottle",16),row("3","bottle",12)),ZoneId.of("UTC"))
        assertEquals(3,s.checkins);assertEquals(2,s.uniquePositions);assertEquals(3.5,s.average!!,0.0001)
        assertEquals(2,s.histogram.sumOf{it.second});assertEquals("Неизвестный стиль",s.styles.single().first)
        assertEquals(3,s.tags.single().second)
    }
    @Test fun calendarYearUsesSelectedZoneAndLeapDay() {
        val records=listOf(row("1","a",null),row("2","b",20,"2024-02-29T12:00:00Z"))
        assertEquals(1,PersonalAnalytics.summarize(records,ZoneId.of("Europe/Istanbul"),2026).checkins)
        assertEquals(0,PersonalAnalytics.summarize(records,ZoneId.of("UTC"),2026).checkins)
        assertEquals(1,PersonalAnalytics.summarize(records,ZoneId.of("UTC"),2024).checkins)
        assertNull(PersonalAnalytics.summarize(emptyList(),ZoneId.of("UTC")).average)
    }
}
