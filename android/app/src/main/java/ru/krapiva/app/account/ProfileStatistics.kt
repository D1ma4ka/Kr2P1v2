package ru.krapiva.app.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.diary.objects

internal data class RatingGroup(val first: Int, val last: Int, val count: Int)
internal data class StyleShare(val name: String, val count: Int)

internal fun groupRatings(histogram: Map<Int, Int>): List<RatingGroup> =
    (1..20 step 4).map { first ->
        val last = first + 3
        RatingGroup(first, last, (first..last).sumOf { histogram[it] ?: 0 })
    }

internal fun compactStyleShares(styles: List<StyleShare>): List<StyleShare> {
    val ranked = styles.filter { it.count > 0 }.sortedByDescending { it.count }
    if (ranked.size <= 6) return ranked
    return ranked.take(5) + StyleShare("Другие стили", ranked.drop(5).sumOf { it.count })
}

@Composable
internal fun ProfileStatistics(stats: JSONObject, onDetails: () -> Unit) {
    val groups = remember(stats) {
        groupRatings(stats.getJSONArray("histogram").objects().associate {
            it.getInt("ratingQuarters") to it.getInt("count")
        })
    }
    val styles = remember(stats) {
        compactStyleShares(stats.getJSONArray("styles").objects().map {
            StyleShare(it.text("name") ?: "Стиль не указан", it.getInt("count"))
        })
    }
    val maxRatingCount = groups.maxOf { it.count }.coerceAtLeast(1)
    ClubCard(Modifier.fillMaxWidth()) {
        Text("Статистика", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Text("${stats.getJSONArray("breweries").length()} пивоварен · ${stats.getInt("earnedBadges")} значков")
        Text("Средняя оценка: ${DiaryNumbers.average(stats.text("averageRating"))}")
        Text("Распределение оценок", style = MaterialTheme.typography.titleMedium)
        if (stats.getInt("ratedCheckins") == 0) {
            Text("Оценок пока нет", color = KraPivaColors.Muted)
        } else {
            for (group in groups) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${DiaryNumbers.rating(group.first)}–${DiaryNumbers.rating(group.last)}: ${group.count}")
                    LinearProgressIndicator(
                        progress = { group.count.toFloat() / maxRatingCount },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Text("Без оценки: ${stats.getInt("unratedCheckins")}", color = KraPivaColors.Muted)

        Text("Стили попробованного пива", style = MaterialTheme.typography.titleMedium)
        if (styles.isEmpty()) {
            Text("Добавь первый чекин, чтобы увидеть доли стилей", color = KraPivaColors.Muted)
        } else {
            StyleDonut(styles, stats.getInt("uniquePositions"))
        }
        OutlinedButton(onDetails, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Подробная статистика")
        }
    }
}

@Composable
private fun ColumnScope.StyleDonut(styles: List<StyleShare>, total: Int) {
    if (total <= 0) return
    val colors = listOf(
        KraPivaColors.Amber,
        KraPivaColors.HopLight,
        Color(0xFF54899E),
        Color(0xFFAE7289),
        Color(0xFF8F79AC),
        MaterialTheme.colorScheme.outline,
    )
    Canvas(Modifier.size(128.dp).align(Alignment.CenterHorizontally)) {
        val stroke = 18.dp.toPx()
        var start = -90f
        styles.forEachIndexed { index, style ->
            val sweep = 360f * style.count / total
            drawArc(
                color = colors[index], startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke),
            )
            start += sweep
        }
    }
    styles.forEachIndexed { index, style ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).background(colors[index], CircleShape))
            val percent = DiaryNumbers.average((100.0 * style.count / total).toString())
            Text("${style.name} · ${style.count} · $percent%", Modifier.weight(1f))
        }
    }
}
