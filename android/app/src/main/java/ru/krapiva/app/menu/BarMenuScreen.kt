package ru.krapiva.app.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.offline.LocalDiscovery
import ru.krapiva.app.offline.AdvisorMode
import ru.krapiva.app.offline.AdvisorProfile
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class MenuLoad(val menu: BarMenu?, val cached: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BarMenuScreen(
    api: AccountApi,
    venueId: String,
    venueName: String,
    expired: () -> Unit,
    closeMenu: () -> Unit,
    openPosition: (String) -> Unit,
    checkin: (MenuPosition) -> Unit,
) {
    var comparedIds by rememberSaveable(venueId) { mutableStateOf(arrayListOf<String>()) }
    var comparing by rememberSaveable(venueId) { mutableStateOf(false) }
    var filter by rememberSaveable(venueId, stateSaver = menuFilterSaver) { mutableStateOf(MenuFilter()) }
    var showFilters by rememberSaveable(venueId) { mutableStateOf(false) }
    var editing by rememberSaveable(venueId) { mutableStateOf<String?>(null) }
    var defaultApplied by rememberSaveable(venueId) { mutableStateOf(false) }
    val owner = api.ownerId()
    val presets = remember(api, owner) { owner?.let { MenuPresetRepository(api, it) } }
    var book by remember(presets) { mutableStateOf<MenuPresetBook?>(null) }
    var presetError by remember(presets) { mutableStateOf<String?>(null) }
    var presetBusy by remember(presets) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun changePresets(change: (MenuPresetBook) -> MenuPresetBook) {
        if (presets == null || book == null || presetBusy) return
        presetBusy = true
        presetError = null
        scope.launch {
            try {
                val next = presets.edit(change)
                if (api.ownerId() == owner) book = next
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                presetError = "Не удалось сохранить настройки фильтра."
            } finally {
                presetBusy = false
            }
        }
    }
    LaunchedEffect(presets) {
        if (presets != null) {
            try {
                val stored = presets.read()
                if (api.ownerId() != owner) return@LaunchedEffect
                book = stored
                if (!defaultApplied) {
                    stored.defaultId?.let(stored::filter)?.let { filter = it }
                    defaultApplied = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                presetError = "Сохранённые фильтры недоступны."
            }
        }
    }
    var retry by remember { mutableIntStateOf(0) }
    val session = api.sessionIdentity()
    var advisorMode by rememberSaveable(venueId) { mutableStateOf(AdvisorMode.MY_CHOICE) }
    var showAdvisor by rememberSaveable(venueId) { mutableStateOf(false) }
    var profile by remember(session, venueId, retry) { mutableStateOf<AdvisorProfile?>(null) }
    var data by remember(session, venueId, retry) { mutableStateOf<MenuLoad?>(null) }
    var error by remember(session, venueId, retry) { mutableStateOf<String?>(null) }
    LaunchedEffect(api, session, venueId, retry) {
        data = null
        error = null
        try {
            val (body, cached) = api.catalogGet("v1/venues/$venueId/menu")
            if (session !== api.sessionIdentity()) return@LaunchedEffect
            data = MenuLoad(BarMenu.read(body), cached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (session !== api.sessionIdentity()) return@LaunchedEffect
            if (e is ApiFailure && e.status == 401) expired()
            error = when {
                e is IOException -> "Нет связи с сервером. Сохранённой копии нет."
                e is ApiFailure && e.status == 404 -> "Заведение больше недоступно."
                else -> "Не удалось загрузить меню."
            }
        }
    }

    val menu = data?.menu
    val compared = remember(menu, comparedIds) { comparisonPositions(comparedIds, menu?.items.orEmpty()) }
    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        comparedIds = ArrayList(compared.map(MenuPosition::positionId))
        if (comparedIds.size < 2) comparing = false
    }
    fun clearComparison() {
        comparedIds = arrayListOf()
        comparing = false
    }
    fun removeFromComparison(id: String) {
        comparedIds = ArrayList(comparedIds - id)
        if (comparedIds.size < 2) comparing = false
    }
    LaunchedEffect(api, session, venueId, retry, menu != null) {
        if (menu == null || profile != null) return@LaunchedEffect
        val history = try {
            api.advisorCheckins()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (session !== api.sessionIdentity()) return@LaunchedEffect
            if (e is ApiFailure && e.status == 401) {
                expired()
                return@LaunchedEffect
            }
            null
        }
        val loaded = withContext(Dispatchers.Default) { LocalDiscovery.advisorProfile(history) }
        if (session === api.sessionIdentity()) profile = loaded
    }
    var advice by remember(menu, filter, profile, advisorMode) { mutableStateOf<MenuAdvice?>(null) }
    LaunchedEffect(menu, filter, profile, advisorMode) {
        val taste = profile ?: return@LaunchedEffect
        advice = withContext(Dispatchers.Default) {
            val candidates = filterMenu(menu?.items.orEmpty(), filter.copy(sort = MenuSort.MENU))
                .filter(MenuPosition::available).map(MenuPosition::advisorCandidate)
            val choice = LocalDiscovery.advise(candidates, taste, AdvisorMode.MY_CHOICE)
            val ranked = if (advisorMode == AdvisorMode.MY_CHOICE) choice else LocalDiscovery.advise(candidates, taste, advisorMode)
            val scores = if (taste.personalized) choice.associate { it.candidate.card.id to it.score } else emptyMap()
            MenuAdvice(ranked, scores)
        }
    }
    val relevance = advice?.scores.orEmpty()
    val rows = remember(menu, filter, relevance) { filterMenu(menu?.items.orEmpty(), filter, relevance) }
    val positions = remember(menu) { menu?.items.orEmpty().associateBy(MenuPosition::positionId) }
    if (showFilters && menu != null) {
        val edit = book?.active?.firstOrNull { it.id == editing }
        MenuFilterDialog(menu.items, edit?.filter ?: filter, relevance.isNotEmpty(), {
            showFilters = false
            editing = null
        }) { next ->
            if (edit == null) { filter = next; defaultApplied = true }
            else changePresets { it.update(edit.id, next, Instant.now()) }
            showFilters = false
            editing = null
        }
    }

    if (comparing && compared.size >= 2 && menu != null) {
        MenuComparisonScreen(venueName, compared, data?.cached == true, menu.isOld(Instant.now()),
            back = { comparing = false }, remove = ::removeFromComparison, clear = ::clearComparison,
            open = openPosition, checkin = checkin)
        return
    }

    if (showAdvisor && menu != null) {
        AdvisorScreen(venueName, advisorMode, profile, advice, positions, data?.cached == true, menu.isOld(Instant.now()),
            selectMode = { advisorMode = it }, refresh = { retry++ }, back = { showAdvisor = false },
            open = openPosition, checkin = checkin)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(closeMenu) { Text("К заведению") }
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ showFilters = true; editing = null }) { Text("Фильтры") }
                                if (compared.isNotEmpty()) {
                                    Button({ comparing = true }, enabled = compared.size >= 2) {
                                        Text("Сравнить (${compared.size})")
                                    }
                                    TextButton(::clearComparison) { Text("Очистить") }
                                }
                            }
                            if (compared.size == 1) Text("Выберите ещё одну позицию.", color = KraPivaColors.Muted)
                            if (compared.size == MAX_MENU_COMPARISON) Text("Выбраны четыре позиции.", color = KraPivaColors.Muted)
                            MenuPresetControls(book, filter, presetBusy, apply = { id, next ->
                                filter = next
                                defaultApplied = true
                                changePresets { it.use(id) }
                            }, editFilter = { preset ->
                                editing = preset.id
                                showFilters = true
                            }, change = ::changePresets)
                            presetError?.let { Text(it, color = KraPivaColors.Error) }
                            ActiveMenuFilters(filter) { filter = it; defaultApplied = true }
                            if (filter.sort == MenuSort.RELEVANCE) {
                                Text(if (relevance.isNotEmpty()) "Подбор по недавним чекинам." else "Пока недостаточно данных для подбора. Сортировка по публичному рейтингу.")
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
        if (menu != null && menu.items.isNotEmpty()) item(key = "advisor") {
            AdvisorPreview(advisorMode, profile, advice, positions, data?.cached == true, menu.isOld(Instant.now()),
                selectMode = { advisorMode = it }, refresh = { retry++ }, showAll = { showAdvisor = true },
                open = openPosition, checkin = checkin)
        }
        if (filter.sort != MenuSort.MENU) {
            items(rows, key = MenuPosition::positionId) { position ->
                MenuPositionCard(position, menu?.sections?.get(position.sectionId), position.positionId in comparedIds,
                    compared.size < MAX_MENU_COMPARISON, { openPosition(position.positionId) }) {
                    comparedIds = ArrayList(toggleComparedPosition(comparedIds, position.positionId, menu?.items.orEmpty()))
                }
            }
        } else menu?.sections?.forEach { (id, title) ->
            val sectionRows = rows.filter { it.sectionId == id }
            if (sectionRows.isNotEmpty()) item(key = "section-$id") {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            items(sectionRows, key = MenuPosition::positionId) { position ->
                MenuPositionCard(position, selected = position.positionId in comparedIds, canAdd = compared.size < MAX_MENU_COMPARISON,
                    open = { openPosition(position.positionId) }) {
                    comparedIds = ArrayList(toggleComparedPosition(comparedIds, position.positionId, menu.items))
                }
            }
        }
    }
}

@Composable
private fun MenuPositionCard(
    position: MenuPosition,
    section: String? = null,
    selected: Boolean,
    canAdd: Boolean,
    open: () -> Unit,
    compare: () -> Unit,
) {
    ClubCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = open)) {
            section?.let { Text(it, color = KraPivaColors.Muted) }
            Text(position.name, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(position.brewery, position.style).joinToString(" · "))
            val details = listOfNotNull(
                position.abv?.let { "${it}%" },
                position.averageRating?.let { "Рейтинг ${DiaryNumbers.average(it.toString())}" },
                position.packaging?.let { formatTitle(formatKey(it)) },
                position.servingFormat?.let(::formatKey)?.takeUnless { it == position.packaging?.let(::formatKey) }?.let(::formatTitle),
                position.volumeMl?.let { "$it мл" },
                position.price?.let { "${it.toPlainString()} ${position.currency}" },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "))
            Text(if (position.available) "В наличии по меню" else "Нет в наличии", color = KraPivaColors.Muted)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(selected, enabled = selected || canAdd, role = Role.Checkbox) { compare() },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(selected, onCheckedChange = null, enabled = selected || canAdd)
            Text(if (selected) "В сравнении" else "Сравнить")
        }
    }
}
