package ru.krapiva.app.catalog

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.R
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.NavigationMark
import ru.krapiva.app.designsystem.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import ru.krapiva.app.diary.objects

@Composable
fun CatalogApp(api: AccountApi, userId: String, upload: suspend (Uri) -> String, onProfile: () -> Unit, expired: () -> Unit, providedModel: CatalogViewModel? = null, onHome: (() -> Unit)? = null, onCheckin: ((JSONObject?) -> Unit)? = null, requestedPosition: String? = null, consumePosition: () -> Unit = {}, bottomBar: @Composable () -> Unit = {},onSettings: () -> Unit = onProfile,onOwnCheckin: (String) -> Unit = {}) {
    val model: CatalogViewModel = providedModel ?: viewModel(key = "catalog-$userId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CatalogViewModel(CatalogRepository(api), upload, expired) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    var firstComparisonId by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var firstComparisonName by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var secondComparisonId by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    fun clearComparison() {
        firstComparisonId = null
        firstComparisonName = null
        secondComparisonId = null
    }
    val first = firstComparisonId
    val second = secondComparisonId
    if (first != null && second != null) PositionComparison(api, first, second) { secondComparisonId = null }
    LaunchedEffect(requestedPosition,state.busy) { if (!state.busy) requestedPosition?.let { model.detail(it); consumePosition() } }
    BackHandler { if (!model.back()) onProfile() }
    KraPivaBackdrop {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            ClubHeader(if(state.page == CatalogPage.POSITIONS) "Пивная база" else null,onSettings,if(state.page == CatalogPage.POSITIONS && onCheckin != null) ({ onCheckin(null) }) else null)
            if (state.page == CatalogPage.POSITIONS && firstComparisonName != null) {
                ListItem(
                    headlineContent = { Text("Открой карточку второй упаковки") },
                    supportingContent = { Text(firstComparisonName.orEmpty()) },
                    trailingContent = { TextButton(::clearComparison) { Text("Сбросить") } },
                )
            }
            if(state.page != CatalogPage.POSITIONS) TextButton(onClick = { if(!model.back()) onProfile() },enabled = !state.busy) { Text("Назад") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive }) }
            state.notice?.let { Text(it, modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            if (state.offline) Text("Нет связи с сервером · сохранённая копия, до 24 часов. Статус мог измениться.", color = KraPivaColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.page) {
                    CatalogPage.POSITIONS, CatalogPage.BREWERIES, CatalogPage.STYLES, CatalogPage.PROPOSALS -> CatalogList(state, model, api)
                    CatalogPage.POSITION, CatalogPage.BREWERY -> Detail(
                        state, model, api, onCheckin, onOwnCheckin,
                        firstComparisonId = firstComparisonId,
                    ) { card ->
                        val id = card.getString("canonicalId")
                        when (firstComparisonId) {
                            null -> {
                                firstComparisonId = id
                                firstComparisonName = card.getString("name")
                            }
                            id -> clearComparison()
                            else -> secondComparisonId = id
                        }
                    }
                    CatalogPage.PROPOSAL -> ProposalForm(state, model)
                    CatalogPage.SCANNER -> ScannerScreen(state, model)
                }
            }
            bottomBar()
        }
    }
}
@Composable internal fun CatalogTitle(value: String) { Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
@Composable internal fun CatalogAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(label) }
}
@Composable internal fun CatalogInput(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun CatalogList(state: CatalogState, model: CatalogViewModel, api: AccountApi) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun search() { keyboard?.hide();focus.clearFocus();model.refresh() }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if(state.page != CatalogPage.POSITIONS) CatalogTitle(when (state.page) { CatalogPage.BREWERIES -> "Пивоварни"; CatalogPage.STYLES -> "Выбрать стиль"; CatalogPage.PROPOSALS -> "Мои предложения"; else -> "Каталог пива" })
                if (state.page == CatalogPage.POSITIONS) {
                    OutlinedTextField(
                        state.filters.q,
                        { model.filters(state.filters.copy(q = it.take(120))) },
                        label = { Text("Поиск пива") },
                        enabled = !state.busy,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClubTokens.CardShape,
                        leadingIcon = { ClubGlyph(ClubIcon.SEARCH) },
                        trailingIcon = {
                            IconButton({ model.open(CatalogPage.SCANNER) }, enabled = !state.busy) {
                                ClubGlyph(ClubIcon.SCAN, description = "Сканировать штрихкод")
                            }
                        },
                    )
                    Button({ search() }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy) {
                        Text("Найти")
                    }
                    Filters(state, model)
                    if (state.queries.isNotEmpty() || state.recent.isNotEmpty()) {
                        OutlinedButton(
                            { showHistory = !showHistory },
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(if (showHistory) "Скрыть историю" else "История и недавние") }
                        if (showHistory) {
                            val recentLabels by produceState<Map<String, String>>(emptyMap(), state.recent) {
                                try { value = ru.krapiva.app.offline.availablePositionLabels(api) }
                                catch (error: Exception) {
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                }
                            }
                            state.queries.forEach { query ->
                                TextButton(
                                    { model.filters(state.filters.copy(q = query)); search() },
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = !state.busy,
                                ) { Text(query) }
                            }
                            state.recent.take(5).forEachIndexed { index, id ->
                                TextButton(
                                    { model.detail(id) },
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = !state.busy,
                                ) { Text(recentLabels[id] ?: "Недавняя позиция ${index + 1}") }
                            }
                            TextButton(model::clearHistory, enabled = !state.busy) {
                                Text("Очистить историю поиска и просмотров")
                            }
                        }
                    }
                } else if (state.page != CatalogPage.PROPOSALS) {
                    CatalogInput("Поиск по названию", state.pickerQuery, !state.busy, model::pickerQuery)
                    CatalogAction("Найти", !state.busy) { model.refresh() }
                }
                if (state.page != CatalogPage.POSITIONS) TextButton({ model.refresh() }, enabled = !state.busy) { Text("Обновить") }
                if (state.items.isEmpty() && !state.busy) {
                    Text(if (state.error != null) "Список не загружен. Повторите запрос." else "Ничего не найдено", style = MaterialTheme.typography.titleLarge)
                    if (state.page == CatalogPage.POSITIONS && state.error == null) {
                        OutlinedButton({ model.proposal() }, Modifier.heightIn(min = 48.dp)) { Text("Предложить позицию") }
                    }
                }
            }
        }
        items(state.items, key = { it.getString("id") }) { item ->
            when (state.page) {
                CatalogPage.POSITIONS -> BeerPositionCard(item, model.repository, state.offline, !state.busy,rating = {CatalogRating(api,item.getString("id"),state.offline)},trailing = { WishlistMark(api,item.getString("id"),state.offline) },onClick = { model.detail(item.getString("id")) })
                CatalogPage.PROPOSALS -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = KraPivaColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.getJSONObject("proposedData").getString("name"), style = MaterialTheme.typography.titleLarge)
                        Text(when (item.getString("status")) { "pending" -> "Ожидает проверки"; "accepted" -> "Принято"; else -> "Отклонено" })
                        item.text("decisionReason")?.let { Text(it) }
                        if (item.getString("status") == "pending") TextButton({ model.proposal(item) }, enabled = !state.busy && !state.offline) { Text("Исправить предложение") }
                        item.text("positionId")?.let { id -> TextButton({ model.detail(id) }, enabled = !state.busy) { Text("Открыть позицию") } }
                    }
                }
                else -> Card(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { if (state.page == CatalogPage.STYLES) model.selectStyle(item) else model.detail(item.getString("id"), true) }, colors = CardDefaults.cardColors(containerColor = KraPivaColors.Surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.getString("name"), style = MaterialTheme.typography.titleLarge)
                        listOfNotNull(item.text("country"), item.text("city")).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = KraPivaColors.Muted) }
                    }
                }
            }
        }
        if (state.next != null) item { CatalogAction("Загрузить ещё", !state.busy) { model.refresh(true) } }
    }
}
@Composable private fun Filters(state: CatalogState, model: CatalogViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (expanded) "Скрыть фильтры" else "Фильтры и разделы")
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ model.open(CatalogPage.BREWERIES) }, enabled = !state.busy) { Text("Пивоварни") }
        TextButton({ model.open(CatalogPage.PROPOSALS) }, enabled = !state.busy) { Text("Мои предложения") }
        TextButton({ model.proposal() }, enabled = !state.busy) { Text("Предложить позицию") }
        StyleChips(state, model)
        Text("Сортировка")
        for ((value, label) in listOf("newest" to "Сначала новые", "name" to "По названию", "abv" to "По крепости"))
            FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KraPivaColors.Amber.copy(alpha = .16f), selectedLabelColor = KraPivaColors.Amber), selected = state.filters.sort == value, enabled = !state.busy, onClick = { model.filters(state.filters.copy(sort = value)) }, label = { Text(label) })
        CatalogInput("Упаковка (если известна)", state.filters.packaging, !state.busy) { model.filters(state.filters.copy(packaging = it.take(80))) }
        CatalogInput("Крепость от, %", state.filters.abvMin, !state.busy) { model.filters(state.filters.copy(abvMin = it.replace(',', '.').take(6))) }
        CatalogInput("Крепость до, %", state.filters.abvMax, !state.busy) { model.filters(state.filters.copy(abvMax = it.replace(',', '.').take(6))) }
        Text("Пивоварня: ${state.filters.breweryLabel.ifBlank { "Все" }}")
        TextButton({ model.open(CatalogPage.BREWERIES) }, enabled = !state.busy) { Text("Выбрать пивоварню") }
        Text("Стиль: ${state.filters.styleLabel.ifBlank { "Все" }}")
        TextButton({ model.open(CatalogPage.STYLES) }, enabled = !state.busy) { Text("Выбрать стиль") }
        CatalogAction("Применить фильтры", !state.busy) { model.refresh() }
        TextButton(model::resetFilters, enabled = !state.busy) { Text("Сбросить фильтры") }
    }
}
@Composable internal fun CatalogImage(data: JSONObject, repository: CatalogRepository, offline: Boolean, large: Boolean = false) {
    val id = data.text("imageAssetId")
    var bitmap by remember(id, offline) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(id, offline) { mutableStateOf(false) }
    LaunchedEffect(id, offline, data) {
        if (id != null) try {
            val bytes = repository.photo(id, offline)
            bitmap = bytes?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            failed = bitmap == null
        } catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error; failed = true }
    }
    val modifier = if (large) Modifier.fillMaxWidth().height(180.dp) else Modifier.width(112.dp).height(112.dp)
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Фото ${data.optString("name")}", modifier.clip(ClubTokens.ControlShape), contentScale = ContentScale.Crop)
    else {
        Image(painterResource(R.drawable.ic_brand_mark), if (id == null) "Фотография отсутствует" else "Фотография недоступна", modifier.padding(8.dp))
        if (failed && large) Text("Не удалось загрузить фото. Обновите карточку.", color = KraPivaColors.Muted)
    }
}
@Composable internal fun BeerPositionCard(data: JSONObject, repository: CatalogRepository, offline: Boolean, enabled: Boolean,rating: @Composable (() -> Unit)? = null,trailing: @Composable (() -> Unit)? = null,onClick: () -> Unit) {
    ClubCard(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogImage(data, repository, offline)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(data.getString("name"), style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(data.getString("breweryName"), color = KraPivaColors.Muted)
                Text(listOfNotNull(data.text("styleName"), data.text("abv")?.let { "$it%" }, data.text("packaging"), data.text("volumeMl")?.let { "$it мл" }).joinToString(" · "), color = KraPivaColors.Muted)
                rating?.invoke()
            }
            trailing?.invoke()
        }
    }
}
@Composable private fun Detail(state: CatalogState, model: CatalogViewModel, api: AccountApi, onCheckin: ((JSONObject?) -> Unit)?, onOwnCheckin: (String) -> Unit,
    firstComparisonId: String?, onCompare: (JSONObject) -> Unit,
) {
    val data = state.detail ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CatalogImage(data, model.repository, state.offline, true)
        CatalogTitle(data.getString("name"))
        Text("Проверено по источнику", color = KraPivaColors.HopLight)
        if (state.page == CatalogPage.POSITION) {
            ru.krapiva.app.moderation.ReportAction(api,"position",data.getString("id"))
        TextButton({ model.detail(data.getString("breweryId"), true) }, enabled = !state.busy) { Text(data.getString("breweryName")) }
            for ((field,label) in listOf("brand" to "Бренд", "styleName" to "Стиль", "abv" to "ABV, %", "ibu" to "IBU", "packaging" to "Упаковка", "volumeMl" to "Объём, мл", "edition" to "Выпуск", "variant" to "Вариант"))
                data.text(field)?.let { Text("$label: $it") }
            val comparisonLabel = when (firstComparisonId) {
                null -> "Сравнить с другой упаковкой"
                data.getString("canonicalId") -> "Убрать из сравнения"
                else -> "Сравнить выбранные упаковки"
            }
            OutlinedButton({ onCompare(data) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy) {
                Text(comparisonLabel)
            }
            if (firstComparisonId == data.getString("canonicalId")) {
                Text("Открой карточку второй упаковки в каталоге.", color = KraPivaColors.Muted)
            }
            if (data.getString("requestedId") != data.getString("canonicalId")) Text("Дубликат объединён. Показана актуальная позиция.", color = KraPivaColors.Muted)
        } else {
            listOfNotNull(data.text("country"),data.text("city")).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
            CatalogAction("Позиции пивоварни", !state.busy, model::selectBrewery)
        }
        data.text("description")?.let { Text(it) }
        Text("Источник: ${data.getString("source")}", color = KraPivaColors.Muted)
        data.text("imageSource")?.let { Text("Источник фото: $it", color = KraPivaColors.Muted) }
        if(state.page == CatalogPage.POSITION) key(data) {
            ru.krapiva.app.offline.AddToCollection(api,data.getString("canonicalId"))
            PositionPersonalHistory(api,data.getString("canonicalId"),onOwnCheckin)
            ru.krapiva.app.diary.PositionDiaryActions(api,data.getString("canonicalId")) { onCheckin?.invoke(data) }
        }
        TextButton({ model.refresh() }, enabled = !state.busy) { Text("Обновить карточку") }
    }
}
