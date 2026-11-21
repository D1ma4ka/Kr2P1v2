package ru.krapiva.app.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers

private data class ComparedPosition(
    val card: JSONObject? = null,
    val statistics: JSONObject? = null,
    val cached: Boolean = false,
) {
    val name: String get() = card?.text("name") ?: "Карточка недоступна"
    val personalRating: String get() = if (statistics == null) "Недоступна"
        else DiaryNumbers.average(statistics.text("personalAverageRating"))
}

@Composable
internal fun PositionComparison(api: AccountApi, first: String, second: String, onClose: () -> Unit) {
    val positions by produceState<List<ComparedPosition>?>(null, api, first, second) {
        suspend fun load(id: String): ComparedPosition {
            val (card, cached) = try {
                api.catalogGet("v1/positions/$id")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                return ComparedPosition()
            }
            val stats = try {
                api.catalogGet("v1/positions/${card.getString("canonicalId")}/statistics")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            return ComparedPosition(card, stats?.first, cached || stats?.second == true)
        }

        value = coroutineScope {
            val firstPosition = async { load(first) }
            val secondPosition = async { load(second) }
            listOf(firstPosition.await(), secondPosition.await())
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Сравнение упаковок") },
        text = {
            val rows = positions
            when {
                rows == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                rows[0].card != null && rows[0].card?.text("canonicalId") == rows[1].card?.text("canonicalId") ->
                    Text("Обе ссылки ведут к одной карточке упаковки.")
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparisonRow("Пиво", rows.map { it.name })
                    ComparisonRow("Пивоварня", rows.map { it.card?.text("breweryName") ?: "—" })
                    ComparisonRow("Упаковка", rows.map { position ->
                        when (val packaging = position.card?.text("packaging")) {
                            "can" -> "Банка"
                            "bottle" -> "Бутылка"
                            "draft", "tap", "keg" -> "Кран"
                            else -> packaging ?: "—"
                        }
                    })
                    ComparisonRow("Объём", rows.map { it.card?.text("volumeMl")?.let { ml -> "$ml мл" } ?: "—" })
                    ComparisonRow("Крепость", rows.map { it.card?.text("abv")?.let { abv -> "$abv%" } ?: "—" })
                    ComparisonRow("Моя оценка", rows.map { it.personalRating })
                    if (rows.any { it.cached }) Text("Часть данных из сохранённой копии", color = KraPivaColors.Muted)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun ComparisonRow(label: String, values: List<String>) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 240.dp || LocalDensity.current.fontScale >= 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                values.forEachIndexed { index, value -> Text("${index + 1}. $value") }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                values.forEachIndexed { index, value -> Text("${index + 1}. $value", Modifier.weight(1f)) }
            }
        }
    }
}
