package ru.krapiva.app.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers

internal const val MAX_MENU_COMPARISON = 4

internal fun comparisonPositions(ids: List<String>, menu: List<MenuPosition>): List<MenuPosition> {
    val byId = menu.associateBy(MenuPosition::positionId)
    return ids.distinct().mapNotNull(byId::get).take(MAX_MENU_COMPARISON)
}

internal fun toggleComparedPosition(ids: List<String>, id: String, menu: List<MenuPosition>): List<String> {
    val selected = comparisonPositions(ids, menu).map(MenuPosition::positionId)
    return when {
        id in selected -> selected - id
        selected.size < MAX_MENU_COMPARISON && menu.any { it.positionId == id } -> selected + id
        else -> selected
    }
}

internal data class ComparisonField(val label: String, val values: List<String?>) {
    val differs get() = values.filterNotNull().distinct().size > 1
}

internal fun MenuPosition.presentation(): String? =
    listOfNotNull(packaging, servingFormat).map(::formatKey).distinct().map(::formatTitle).joinToString(" · ").ifBlank { null }

internal fun comparisonFields(positions: List<MenuPosition>): List<ComparisonField> {
    val rating = { value: Double? -> value?.let { DiaryNumbers.average(it.toString()) } }
    fun field(label: String, value: (MenuPosition) -> String?) = ComparisonField(label, positions.map(value))
    return listOf(
        field("Пивоварня") { it.brewery },
        field("Стиль") { it.style },
        field("Крепость · ABV") { it.abv?.let { number -> "${number}%" } },
        field("Горечь · IBU") { it.ibu?.toString() },
        field("Средний рейтинг") { rating(it.averageRating) },
        field("Число оценок") { it.ratedCheckins?.toString() },
        field("Моя средняя оценка") { rating(it.personalAverageRating) },
        field("Моя история") { if (it.tried) "Пробовал" else "Не пробовал" },
        field("Мои чекины") { it.personalCheckins?.toString() },
        field("Упаковка и подача") { it.presentation() },
        field("Объём") { it.volumeMl?.let { ml -> "$ml мл" } },
        field("Цена в заведении") { position ->
            position.price?.let { price -> position.currency?.let { "${price.stripTrailingZeros().toPlainString()} $it" } }
        },
        field("Доступность") { if (it.available) "В наличии по меню" else "Нет в наличии" },
        field("Описание") { it.description },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MenuComparisonScreen(
    venueName: String,
    positions: List<MenuPosition>,
    cached: Boolean,
    old: Boolean,
    back: () -> Unit,
    remove: (String) -> Unit,
    clear: () -> Unit,
    open: (String) -> Unit,
    checkin: (MenuPosition) -> Unit,
) {
    BackHandler(onBack = back)
    val fields = remember(positions) { comparisonFields(positions) }
    val labels = positions.mapIndexed { index, position ->
        listOfNotNull("${index + 1}. ${position.name}", position.presentation(), position.volumeMl?.let { "$it мл" }).joinToString(" · ")
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp && LocalDensity.current.fontScale < 1.4f
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Сравнение · $venueName", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                if (cached) Text("Данные из сохранённой копии.", color = KraPivaColors.Muted)
                if (old || cached) Text("Наличие могло измениться. Уточните в заведении.", color = KraPivaColors.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(back) { Text("К меню") }
                    TextButton(clear) { Text("Очистить выбор") }
                }
                Text("Отличия отмечены в характеристиках.", color = KraPivaColors.Muted)
            }
            items(positions.indices.toList(), key = { positions[it].positionId }) { index ->
                val position = positions[index]
                ClubCard {
                    Text(labels[index], Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton({ open(position.positionId) }) { Text("Карточка") }
                        TextButton({ checkin(position) }) { Text("Чекин") }
                        TextButton({ remove(position.positionId) }) { Text("Убрать") }
                    }
                }
            }
            items(fields, key = ComparisonField::label) { field ->
                ClubCard {
                    Text(field.label, Modifier.semantics { heading() },
                        color = if (field.differs) KraPivaColors.Amber else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium)
                    if (field.differs) Text("Различается", style = MaterialTheme.typography.labelMedium)
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            field.values.forEachIndexed { index, value ->
                                ComparisonValue(labels[index], value, Modifier.weight(1f))
                            }
                        }
                    } else {
                        field.values.forEachIndexed { index, value -> ComparisonValue(labels[index], value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonValue(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = KraPivaColors.Muted, style = MaterialTheme.typography.bodySmall)
        Text(value ?: "Нет данных")
    }
}
