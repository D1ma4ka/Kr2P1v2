package ru.krapiva.app.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.offline.CachedPersonalSource
import ru.krapiva.app.offline.LocalDiscovery
import ru.krapiva.app.offline.RecommendationCard
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class MenuLoad(val menu: BarMenu?, val cached: Boolean)

@Composable
internal fun BarMenuScreen(
    api: AccountApi,
    venueId: String,
    venueName: String,
    expired: () -> Unit,
    openPosition: (String) -> Unit,
) {
    var filter by rememberSaveable(venueId, stateSaver = menuFilterSaver) { mutableStateOf(MenuFilter()) }
    var showFilters by rememberSaveable(venueId) { mutableStateOf(false) }
    var relevance by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var retry by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<MenuLoad?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, venueId, retry) {
        data = null
        error = null
        try {
            val owner = api.ownerId()
            val (body, cached) = api.catalogGet("v1/venues/$venueId/menu")
            if (owner != api.ownerId()) return@LaunchedEffect
            data = MenuLoad(BarMenu.read(body), cached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiFailure && e.status == 401) expired()
            error = when {
                e is IOException -> "Нет связи с сервером. Сохранённой копии нет."
                e is ApiFailure && e.status == 404 -> "Заведение больше недоступно."
                else -> "Не удалось загрузить меню."
            }
        }
    }

    val menu = data?.menu
    LaunchedEffect(menu, api) {
        relevance = emptyMap()
        if (menu != null) {
            try {
                val owner = api.ownerId()
                val history = CachedPersonalSource(api).snapshot().checkins
                val candidates = menu.items.map { RecommendationCard(it.positionId, it.name, it.brewery, it.style) }
                val scores = LocalDiscovery.select(candidates, history).associate { it.card.id to it.score }
                if (owner == api.ownerId()) relevance = scores
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The menu works independently of the local discovery cache.
            }
        }
    }
    val rows = remember(menu, filter, relevance) { filterMenu(menu?.items.orEmpty(), filter, relevance) }
    if (showFilters && menu != null) {
        MenuFilterDialog(menu.items, filter, relevance.isNotEmpty(), { showFilters = false }) {
            filter = it
            showFilters = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Меню · $venueName", style = MaterialTheme.typography.headlineSmall)
            val loaded = data
            when {
                error != null -> {
                    Text(error.orEmpty(), color = KraPivaColors.Error)
                    TextButton({ retry++ }) { Text("Повторить") }
                }
                loaded == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    val menu = loaded.menu
                    if (loaded.cached) {
                        Text("Свежие данные недоступны. Показана сохранённая копия.", color = KraPivaColors.Muted)
                        TextButton({ retry++ }) { Text("Обновить") }
                    }
                    if (menu == null) Text("У заведения пока нет опубликованного меню.")
                    else {
                        val updated = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withZone(ZoneId.systemDefault()).format(menu.updatedAt)
                        Text("Обновлено $updated", color = KraPivaColors.Muted)
                        Text(menu.source, color = KraPivaColors.Muted)
                        if (menu.isOld(Instant.now())) Text("Наличие могло измениться. Уточните в заведении.")
                        if (menu.items.isEmpty()) Text("В опубликованном меню пока нет позиций.")
                        else {
                            OutlinedButton({ showFilters = true }) { Text("Фильтры") }
                            ActiveMenuFilters(filter) { filter = it }
                            if (filter.sort == MenuSort.RELEVANCE) {
                                Text(if (relevance.isNotEmpty()) "Подбор по доступной истории на устройстве." else "Пока недостаточно данных для подбора. Сортировка по рейтингу.")
                            }
                            if (rows.isEmpty()) {
                                Text("По этим условиям ничего не найдено.")
                                val chips = filter.chips()
                                val suggestion = chips.firstOrNull { filterMenu(menu.items, it.cleared, relevance).isNotEmpty() } ?: chips.firstOrNull()
                                if (suggestion != null) TextButton({ filter = suggestion.cleared }) { Text("Убрать · ${suggestion.label}") }
                            }
                        }
                    }
                }
            }
        }
        if (filter.sort != MenuSort.MENU) {
            items(rows, key = MenuPosition::positionId) { position ->
                MenuPositionCard(position, menu?.sections?.get(position.sectionId)) { openPosition(position.positionId) }
            }
        } else menu?.sections?.forEach { (id, title) ->
            val sectionRows = rows.filter { it.sectionId == id }
            if (sectionRows.isNotEmpty()) item(key = "section-$id") {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            items(sectionRows, key = MenuPosition::positionId) { position ->
                MenuPositionCard(position) { openPosition(position.positionId) }
            }
        }
    }
}

@Composable
private fun MenuPositionCard(position: MenuPosition, section: String? = null, open: () -> Unit) {
    ClubCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = open)) {
            section?.let { Text(it, color = KraPivaColors.Muted) }
            Text(position.name, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(position.brewery, position.style).joinToString(" · "))
            val details = listOfNotNull(
                position.abv?.let { "${it}%" },
                position.averageRating?.let { "Рейтинг ${DiaryNumbers.average(it.toString())}" },
                (position.servingFormat ?: position.packaging)?.let { formatTitle(formatKey(it)) },
                position.volumeMl?.let { "$it мл" },
                position.price?.let { "${it.toPlainString()} ${position.currency}" },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "))
            Text(if (position.available) "В наличии по меню" else "Нет в наличии", color = KraPivaColors.Muted)
        }
    }
}
