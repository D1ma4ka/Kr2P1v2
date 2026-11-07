package ru.krapiva.app.diary

import java.math.BigDecimal
import java.math.RoundingMode

object DiaryNumbers {
    /** null means unrated, zero is never a rating. */
    fun quarters(text: String): Int? {
        if (text.isBlank()) return null
        require(text.length <= 16 && !text.contains('e',ignoreCase = true)) { "Введите обычное число от 0,25 до 5" }
        val n = text.trim().replace(',', '.').toBigDecimalOrNull() ?: throw IllegalArgumentException("Введите оценку от 0,25 до 5 с шагом 0,25")
        val q = runCatching { n.multiply(BigDecimal(4)).intValueExact() }.getOrNull()
        require(q != null && q in 1..20) { "Оценка от 0,25 до 5 с шагом 0,25" }
        return q
    }
    fun rating(quarters: Int): String = BigDecimal(quarters).divide(BigDecimal(4)).stripTrailingZeros().toPlainString().replace('.', ',')
    fun average(value: String?): String = value?.toBigDecimalOrNull()?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',') ?: "Нет оценок"
}
