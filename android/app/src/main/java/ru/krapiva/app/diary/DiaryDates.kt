package ru.krapiva.app.diary

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object DiaryDates {
    private val minute = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun display(value: String,zone: ZoneId = ZoneId.systemDefault()): String = runCatching { minute.format(Instant.parse(value).atZone(zone)) }.getOrDefault(value)
    fun instant(value: String,zone: ZoneId = ZoneId.systemDefault()): String = LocalDateTime.parse(value,minute).atZone(zone).toInstant().toString()
    fun boundary(value: String,end: Boolean,zone: ZoneId = ZoneId.systemDefault()): String {
        if(value.isBlank()) return ""
        val date = LocalDate.parse(value)
        return if(end)date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1000).toString() else date.atStartOfDay(zone).toInstant().toString()
    }
}
