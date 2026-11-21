package ru.krapiva.app.diary

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.NavigationMark
import ru.krapiva.app.catalog.CatalogRepository
import ru.krapiva.app.catalog.BeerPositionCard
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*

@Composable
fun DiaryApp(api: AccountApi,userId: String,request: Int,entry: String,position: JSONObject?,upload: suspend (Uri) -> String,onProfile: () -> Unit,onCatalog: () -> Unit,onPosition: (String) -> Unit,expired: () -> Unit,providedModel: DiaryViewModel? = null,onSocial: (String?) -> Unit = {}, onHome: () -> Unit = onProfile, bottomBar: @Composable ((() -> Unit) -> Unit) -> Unit = {},venue: JSONObject?=null,onVenue: (String)->Unit={},onBadges: ()->Unit={},onPlaces: ()->Unit={}) {
    val model = providedModel ?: viewModel<DiaryViewModel>(key = "diary-$userId",factory = object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = DiaryViewModel(api,upload,expired) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(request) { model.entry(request,entry,position,venue) }
    if(state.success && state.data != null) {
        BackHandler { model.dismissSuccess() }
        CheckinSuccess(state.data!!,api,model::dismissSuccess,{ model.new() },{ model.dismissSuccess();onSocial(null) })
        return
    }
    state.publishCandidates?.let { candidates ->
        AlertDialog(
            onDismissRequest = model::cancelPublishCheck,
            title = { Text(if(candidates.isEmpty()) "Проверь карточку пива" else "Похоже, это пиво уже есть в KraPiva!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if(candidates.isEmpty()) Text("Совпадений не найдено. Проверьте исходные данные перед публикацией.")
                    else {
                        Text("Выберите одну подходящую позицию или укажите, что это другое пиво.")
                        candidates.take(5).forEach { candidate ->
                            OutlinedButton({ model.confirmPublish(candidate.getString("id")) },modifier=Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment=Alignment.Start) {
                                    Text(candidate.getString("name"));Text(candidate.getString("breweryName"),color=KraPivaColors.Muted)
                                    Text(listOfNotNull(candidate.text("styleName"),candidate.text("abv")?.let{"$it%"},candidate.text("packaging"),candidate.text("volumeMl")?.let{"$it мл"}).joinToString(" · "),color=KraPivaColors.Muted)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ model.confirmPublish() }) { Text(if(candidates.isEmpty()) "Подтвердить проверку" else "Это другое пиво") } },
            dismissButton = { TextButton(model::cancelPublishCheck) { Text("Исправить черновик") } }
        )
    }
    var leave by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val back = { if(state.page == DiaryPage.EDITOR) leave = true else if(state.page == DiaryPage.HOME) onHome() else model.open(DiaryPage.HOME) }
    BackHandler { back() }
    KraPivaBackdrop {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            if(state.page != DiaryPage.EDITOR) {
                ClubHeader("Моё пиво",onCheckin = {model.new()})
                if(state.page==DiaryPage.HOME)Row {TextButton(onBadges){Text("Достижения")};TextButton(onPlaces){Text("Места и карта")}}
                if(state.page != DiaryPage.HOME) TextButton(back,enabled = !state.busy) {Text("Назад")}
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it,color = KraPivaColors.Error,modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive }) }
            state.notice?.let { Text(it,color = KraPivaColors.Amber,modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            if(state.offline) Text("Нет связи · сохранённая копия до 24 часов. Данные могли измениться.",modifier = Modifier.padding(horizontal = 16.dp),color = KraPivaColors.Muted)
            Box(Modifier.weight(1f)) {
                when(state.page) {
                    DiaryPage.NOTIFICATIONS -> ru.krapiva.app.offline.NotificationsScreen(api) { model.open(DiaryPage.valueOf(it)) }
                    DiaryPage.YEAR -> ru.krapiva.app.offline.YearScreen(api)
                    DiaryPage.DISCOVERY -> ru.krapiva.app.offline.DiscoveryScreen(api,onPosition,onCatalog)
                    DiaryPage.TASTE -> ru.krapiva.app.offline.TasteScreen(api)
                    DiaryPage.COLLECTIONS -> ru.krapiva.app.offline.CollectionsScreen(api,onPosition,onCatalog)
                    DiaryPage.EDITOR -> DiaryEditor(state,model,back,{ pendingNavigation = { model.new() };leave = true })
                    DiaryPage.DETAIL -> CheckinDetail(state,model,onPosition,onSocial,onVenue)
                    DiaryPage.STATISTICS -> StatisticsScreen(state,model)
                    else -> DiaryList(state,model,onPosition,onSocial)
                }
            }
            bottomBar { action ->
                if(state.page == DiaryPage.EDITOR) { pendingNavigation = action;leave = true } else action()
            }
        }
    }
    if(leave) AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { leave = false;pendingNavigation = null },title = { Text("Выйти из редактора?") },text = { Text("Перед выходом изменения будут сохранены в зашифрованный черновик. На сервер они отправляются отдельно.") },confirmButton = { TextButton({ leave = false;val action = pendingNavigation;pendingNavigation = null;model.leaveEditor { if(action != null)action() else model.open(DiaryPage.HOME) } }) { Text("Выйти") } },dismissButton = { TextButton({ leave = false;pendingNavigation = null }) { Text("Остаться") } })
}
@Composable internal fun DiaryTitle(value: String) { Text(value,style = MaterialTheme.typography.headlineMedium,modifier = Modifier.semantics { heading() }) }
@Composable internal fun DiaryButton(text: String,enabled: Boolean = true,action: () -> Unit) { Button(action,Modifier.fillMaxWidth().heightIn(min = 52.dp),enabled = enabled) { Text(text) } }
@Composable internal fun DiaryCard(content: @Composable ColumnScope.() -> Unit) { ClubCard(Modifier.fillMaxWidth(),content = content) }
@Composable private fun Summary(stats: JSONObject) {
    DiaryCard { Text("Всего чекинов: ${stats.getInt("totalCheckins")}");Text("Уникальных позиций: ${stats.getInt("uniquePositions")}");Text("Средняя оценка: ${DiaryNumbers.average(stats.text("averageRating"))}");Text("Без оценки: ${stats.getInt("unratedCheckins")}",color = KraPivaColors.Muted) }
}
@Composable private fun DiaryList(state: DiaryState,model: DiaryViewModel,onPosition: (String) -> Unit,onSocial: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { if(state.page != DiaryPage.HOME) DiaryTitle(when(state.page) { DiaryPage.HOME -> "Моё пиво";DiaryPage.DIARY -> "История чекинов";DiaryPage.UNIQUE -> "Попробованное пиво";DiaryPage.WISHLIST -> "Хочу попробовать";DiaryPage.PICK -> "Выберите позицию для чекина";else -> "Черновики · не отправлено" }) }
        if(state.page == DiaryPage.HOME) {
            state.statistics?.let { stats -> item { Summary(stats);Text("Получено значков: ${stats.getInt("earnedBadges")}",color = KraPivaColors.Muted) } }
            item {
                DiarySection("Попробованное") {
                    DiarySectionAction("Попробованное пиво", ClubIcon.BOTTLE) { model.open(DiaryPage.UNIQUE) }
                    DiarySectionAction("История чекинов", ClubIcon.GLASS) { model.open(DiaryPage.DIARY) }
                }
            }
            item {
                DiarySection("Мои списки") {
                    DiarySectionAction("Хочу попробовать", ClubIcon.BOOKMARK) { model.open(DiaryPage.WISHLIST) }
                    DiarySectionAction("Подборки", ClubIcon.CATALOG) { model.open(DiaryPage.COLLECTIONS) }
                }
            }
            item { Text("Последние чекины",style = MaterialTheme.typography.titleLarge) }
        } else if(state.page == DiaryPage.PICK) item { OutlinedTextField(state.pickerQuery,model::pickerQuery,label = { Text("Поиск позиции") },modifier = Modifier.fillMaxWidth());DiaryButton("Найти позицию",!state.busy) { model.refresh() } }
        else if(state.page != DiaryPage.DRAFTS) item { DiaryFilterPanel(state,model) }
        if (state.page != DiaryPage.HOME || state.error != null) {
            item { TextButton({ model.refresh() },enabled = !state.busy) { Text("Обновить") } }
        }
        if(state.items.isEmpty() && !state.busy) item { Text("Пока нет записей",color = KraPivaColors.Muted) }
        items(state.items,key = { it.optString("id",it.optString("draftId")) }) { item ->
            when(state.page) {
                DiaryPage.PICK -> BeerPositionCard(item,CatalogRepository(model.api),state.offline,!state.busy) { model.select(item) }
                DiaryPage.UNIQUE,DiaryPage.WISHLIST -> DiaryCard {
                    Text(item.getString("name"),style = MaterialTheme.typography.titleLarge);Text(item.getString("breweryName"));item.text("styleName")?.let { Text(it,color = KraPivaColors.Muted) }
                    if(state.page == DiaryPage.UNIQUE) Text("Чекинов: ${item.getInt("checkins")} · средняя ${DiaryNumbers.average(item.text("averageRating"))}")
                    TextButton({ onPosition(item.getString("id")) },enabled = !state.busy) { Text("Открыть позицию") }
                    if (state.page == DiaryPage.UNIQUE) item.text("releaseId")?.let { releaseId ->
                        TextButton({ model.open(DiaryPage.DIARY, DiaryFilters(releaseId = releaseId)) }, enabled = !state.busy) {
                            Text("Все чекины этого пива")
                        }
                    }
                }
                DiaryPage.DRAFTS -> DiaryCard {
                    Text(item.optString("positionName","Черновик"));Text("Не отправлено",color = KraPivaColors.Amber);Text(item.optString("body"))
                    TextButton({ model.edit(item) },enabled = !state.busy) { Text("Продолжить черновик") };TextButton({ model.removeDraft(item.getString("draftId")) },enabled = !state.busy) { Text("Удалить черновик") }
                }
                else -> ClubCheckinCard(item,model.api,state.offline,!state.busy) { model.detail(item.getString("id")) }
            }
        }
        if(state.next != null && state.page != DiaryPage.HOME) item { DiaryButton("Загрузить ещё",!state.busy) { model.refresh(true) } }
        if (state.page == DiaryPage.HOME) {
            item {
                DiarySection("Статистика и инструменты") {
                    for ((page, title) in listOf(
                        DiaryPage.DRAFTS to "Черновики",
                        DiaryPage.STATISTICS to "Статистика",
                        DiaryPage.TASTE to "Мой вкус",
                        DiaryPage.DISCOVERY to "Открытия по вкусу",
                        DiaryPage.YEAR to "Мой год",
                        DiaryPage.NOTIFICATIONS to "Уведомления",
                    )) {
                        DiarySectionAction(title) { model.open(page) }
                    }
                    DiarySectionAction("Друзья и лента", ClubIcon.PEOPLE) { onSocial(null) }
                }
            }
        }
    }
}

@Composable
private fun DiarySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Column(content = content)
    }
}

@Composable
private fun DiarySectionAction(title: String, icon: ClubIcon? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = icon?.let { marker -> { ClubGlyph(marker) } },
        trailingContent = { ClubGlyph(ClubIcon.CHEVRON) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = KraPivaColors.Surface),
    )
}
@Composable internal fun CheckinCard(data: JSONObject,enabled: Boolean,action: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled,onClick = action),colors = CardDefaults.cardColors(containerColor = KraPivaColors.Surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(data.getString("positionName"),style = MaterialTheme.typography.titleLarge);Text(data.getString("breweryName"),color = KraPivaColors.Muted)
            Text(DiaryDates.display(data.getString("occurredAt")));Text(if(data.isNull("ratingQuarters")) "Без оценки" else "Оценка: ${DiaryNumbers.rating(data.getInt("ratingQuarters"))}",color = KraPivaColors.Amber)
            if(data.optString("body").isNotBlank()) Text(data.getString("body"))
            val tags = data.optJSONArray("tagLabels")?.strings().orEmpty();if(tags.isNotEmpty()) Text(tags.joinToString(" · "),color = KraPivaColors.Muted)
            data.text("servingFormat")?.let { Text("Подача: $it") };(data.text("venueName") ?: data.text("venueText"))?.let { Text("Место: $it"+if(data.text("venueId")==null)" · не подтверждено" else "") }
            Text("Фото: ${data.optJSONArray("mediaIds")?.length() ?: 0}",color = KraPivaColors.Muted)
        }
    }
}
@Composable private fun DiaryFilterPanel(state: DiaryState,model: DiaryViewModel) {
    var expanded by remember(state.page) { mutableStateOf(false) }
    OutlinedTextField(state.filters.q,{ model.filters(state.filters.copy(q = it.take(120))) },label = { Text("Название, заметка или стиль") },modifier = Modifier.fillMaxWidth())
    TextButton({ expanded = !expanded }) { Text(if(expanded)"Скрыть фильтры" else "Фильтры") }
    if(expanded) {
        if(state.page == DiaryPage.DIARY) {
            for((value,label,change) in listOf(Triple(state.filters.from,"От (ГГГГ-ММ-ДД)",{v: String -> model.filters(state.filters.copy(from = v))}),Triple(state.filters.to,"До (ГГГГ-ММ-ДД)",{v: String -> model.filters(state.filters.copy(to = v))}),Triple(state.filters.ratingMin,"Оценка от",{v: String -> model.filters(state.filters.copy(ratingMin = v))}),Triple(state.filters.ratingMax,"Оценка до",{v: String -> model.filters(state.filters.copy(ratingMax = v))}),Triple(state.filters.venue,"Название места",{v: String -> model.filters(state.filters.copy(venue = v))}))) OutlinedTextField(value,change,label = { Text(label) },modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.filters.unrated,{ model.filters(state.filters.copy(unrated = it)) });Text("Без оценки") }
            Row { TextButton({ model.filters(state.filters.copy(photo = "true")) }) { Text("С фото") };TextButton({ model.filters(state.filters.copy(photo = "false")) }) { Text("Без фото") };TextButton({ model.filters(state.filters.copy(photo = "")) }) { Text("Любые фото") } }
        }
        val sorts = if(state.page == DiaryPage.DIARY) listOf("date_desc" to "Новые","date_asc" to "Старые","rating_desc" to "Оценка ↓","rating_asc" to "Оценка ↑") else listOf("name" to "Название","latest" to "Последние","rating" to "Оценка")
        for((sort,label) in sorts) Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.filters.sort == sort,{ model.filters(state.filters.copy(sort = sort)) });Text(label) }
        if(state.filters.styleId.isNotBlank())Text("Выбран стиль · фильтр активен",color = KraPivaColors.Amber)
        StyleFilterSelector(model.api,state.busy) { model.filters(state.filters.copy(styleId = it)) }
        TextButton({ model.open(DiaryPage.STATISTICS) },enabled = !state.busy) { Text("Пивоварни и диаграммы") }
        TextButton({ model.filters(DiaryFilters());model.refresh() },enabled = !state.busy) { Text("Сбросить фильтры") }
    }
    DiaryButton("Применить поиск",!state.busy) { model.refresh() }
}
@Composable private fun CheckinDetail(state: DiaryState,model: DiaryViewModel,onPosition: (String) -> Unit,onSocial: (String?) -> Unit,onVenue: (String)->Unit) {
    val data = state.data ?: return
    var confirm by remember(data.getString("id")) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiaryTitle("Мой чекин");CheckinCard(data,false) { }
        ru.krapiva.app.offline.ShareCheckin(model.api,data)
        data.getJSONArray("mediaIds").strings().forEach { DiaryPhoto(it,model.api,state.offline) }
        TextButton({ onPosition(data.getString("positionId")) },enabled = !state.busy) { Text("Открыть позицию") }
        if(data.text("venueId") != null) TextButton({onVenue(data.getString("venueId"))}) { Text("Открыть место на карте") }
        Text("Видимость: ${when(data.getString("visibility")) { "public" -> "Все";"friends" -> "Друзья";else -> "Только я" }}",color = KraPivaColors.Muted)
        DiaryButton("Лайки и комментарии",!state.busy && !state.offline) { onSocial(data.getString("id")) }
        DiaryButton("Редактировать чекин",!state.busy) { model.edit(data) }
        TextButton({ confirm = true },enabled = !state.busy && !state.offline) { Text("Удалить чекин") }
        TextButton({ model.refresh() },enabled = !state.busy) { Text("Обновить запись") }
    }
    if(confirm) AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { confirm = false },title = { Text("Удалить чекин?") },text = { Text("Коллекция и статистика будут пересчитаны. Повторы этой позиции сохранятся.") },confirmButton = { TextButton({ confirm = false;model.delete() }) { Text("Удалить") } },dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } })
}
@Composable internal fun DiaryPhoto(id: String,api: AccountApi,offline: Boolean) {
    var bitmap by remember(id,offline) { mutableStateOf<android.graphics.Bitmap?>(null) };var error by remember(id) { mutableStateOf(false) };var retry by remember(id) { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(id,offline,retry) { try { val bytes = api.photo(id,offline);bitmap = bytes?.let { BitmapFactory.decodeByteArray(it,0,it.size) };error = bitmap == null } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error = true } }
    // Reserve the same viewport while loading, offline or failed: later images must not move actions.
    Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),"Фото чекина",Modifier.fillMaxSize().clickable { full = true }) }
            ?: if(error)TextButton({ retry++ }) { Text("Фото недоступно · повторить") }
            else Text("Загружаем фото…",color = KraPivaColors.Muted)
    }
    if(full)AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { full = false },title = { Text("Фото чекина") },text = { bitmap?.let { Image(it.asImageBitmap(),"Просмотр фото",Modifier.fillMaxWidth().height(240.dp)) } },confirmButton = { TextButton({ full = false }) { Text("Закрыть") } })
}
@Composable private fun StatisticsScreen(state: DiaryState,model: DiaryViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiaryTitle("Моя статистика");TextButton({ model.refresh() },enabled = !state.busy) { Text("Обновить статистику") }
        val stats = state.statistics ?: return@Column
        Summary(stats)
        if(stats.getInt("totalCheckins") == 0) Text("Запишите первый чекин — здесь появятся ваши данные.",color = KraPivaColors.Muted)
        stats.getJSONArray("styles").objects().firstOrNull { it.text("name") != null }?.let { Text("Чаще встречается стиль: ${it.getString("name")}",color = KraPivaColors.Muted) }
        Text("Стили · уникальные позиции",style = MaterialTheme.typography.titleLarge)
        val total = stats.getInt("uniquePositions")
        Text("Доли от всех уникальных позиций: $total, включая неизвестный стиль.",color = KraPivaColors.Muted)
        stats.getJSONArray("styles").objects().forEach { style ->
            val id = style.text("id") ?: "unknown"
            StatBar(style.text("name") ?: "Неизвестный стиль",style.getInt("count"),total) { model.open(DiaryPage.UNIQUE,DiaryFilters(styleId = id,sort = "name")) }
            if(id != "unknown")TextButton({ model.open(DiaryPage.DIARY,DiaryFilters(styleId = id)) }) { Text("Чекины этого стиля") }
        }
        Text("Пивоварни · уникальные позиции",style = MaterialTheme.typography.titleLarge)
        stats.getJSONArray("breweries").objects().forEach { brewery -> StatBar(brewery.getString("name"),brewery.getInt("count"),total) { model.open(DiaryPage.UNIQUE,DiaryFilters(breweryId = brewery.getString("id"),sort = "name")) } }
        Text("Оценки · все чекины",style = MaterialTheme.typography.titleLarge)
        val bins = stats.getJSONArray("histogram").objects();val maximum = bins.maxOfOrNull { it.getInt("count") }?.coerceAtLeast(1) ?: 1
        bins.forEach { bin -> val q = bin.getInt("ratingQuarters");StatBar(DiaryNumbers.rating(q),bin.getInt("count"),maximum,false) { val rating = DiaryNumbers.rating(q);model.open(DiaryPage.DIARY,DiaryFilters(ratingMin = rating,ratingMax = rating)) } }
        TextButton({ model.open(DiaryPage.DIARY,DiaryFilters(unrated = true)) }) { Text("Без оценки: ${stats.getInt("unratedCheckins")}") }
        Text("Средняя только по оценённым; округление до двух знаков.",color = KraPivaColors.Muted)
    }
}
@Composable private fun StatBar(label: String,count: Int,total: Int,share: Boolean = true,action: () -> Unit) {
    val fraction = if(total > 0)count.toFloat()/total else 0f
    Column(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "$label: $count" }) {
        Text("$label: $count" + if(share && total > 0)" · ${DiaryNumbers.average((fraction*100).toString())}%" else "")
        Box(Modifier.fillMaxWidth().height(10.dp).background(KraPivaColors.Surface)) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0f,1f)).fillMaxHeight().background(KraPivaColors.Amber)) }
    }
}
@Composable fun ProfileDiarySummary(api: AccountApi,open: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) };var error by remember { mutableStateOf(false) };var offline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { val result = api.catalogGet("v1/me/statistics");data = result.first;offline = result.second }catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error = true } }
    data?.let { Summary(it) };if(offline)Text("Статистика · сохранённая копия",color = KraPivaColors.Muted)
    if(error)Text("Статистика недоступна. Откройте раздел и повторите запрос.",color = KraPivaColors.Muted)
    Row { TextButton({ open("DIARY") }) { Text("Коллекция") };TextButton({ open("STATISTICS") }) { Text("Статистика") } }
    Row { TextButton({ open("UNIQUE") }) { Text("Уникальные") };TextButton({ open("WISHLIST") }) { Text("Wishlist") } }
}
@Composable fun PositionDiaryActions(api: AccountApi,id: String,newCheckin: () -> Unit) {
    var stats by remember(id) { mutableStateOf<JSONObject?>(null) };var wished by remember(id) { mutableStateOf(false) };var busy by remember(id) { mutableStateOf(true) };var error by remember(id) { mutableStateOf<String?>(null) };var offline by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { try { val s = api.catalogGet("v1/positions/$id/statistics");stats = s.first;offline = s.second;wished = api.catalogGet("v1/me/wishlist/$id").first.getBoolean("wished") }catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error = "Агрегаты или wishlist недоступны. Обновите карточку." }finally { busy = false } }
    DiaryButton("Сделать чекин",!busy,newCheckin)
    stats?.let { s -> DiaryCard { Text("Доступные вам чекины: ${s.getInt("totalCheckins")} · пользователей: ${s.getInt("users")}");Text("Оценок: ${s.getInt("ratedCheckins")} · средняя: ${DiaryNumbers.average(s.text("averageRating"))}");Text("Лично: ${s.getInt("personalCheckins")} чекинов · средняя ${DiaryNumbers.average(s.text("personalAverageRating"))}",color = KraPivaColors.Muted);if(offline)Text("Сохранённые агрегаты",color = KraPivaColors.Muted) } }
    error?.let { Text(it,color = KraPivaColors.Error) }
    TextButton({ busy = true;scope.launch { try { val result = api.json("v1/me/wishlist/$id",if(wished)"DELETE" else "PUT");wished = result.getBoolean("wished");api.invalidateDiary();error = null }catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error = if(e is ru.krapiva.app.account.ApiFailure)e.message else if(e is java.io.IOException)"Не удалось изменить wishlist. Нужна связь с сервером." else "Не удалось изменить wishlist. Повторите запрос." }finally { busy = false } } },enabled = !busy && !offline) { Text(if(wished)"Убрать из wishlist" else "Хочу попробовать") }
}


@Composable private fun StyleFilterSelector(api: AccountApi,disabled: Boolean,select: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) };var query by remember { mutableStateOf("") };var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) };var next by remember { mutableStateOf<String?>(null) };var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean = false) {
        if(busy)return
        busy = true;scope.launch { try {
            val result = api.catalogGet("v1/styles?q=${Uri.encode(query)}&limit=20" + if(more)next?.let { "&cursor=${Uri.encode(it)}" }.orEmpty() else "")
            rows = ((if(more) rows else emptyList()) + result.first.getJSONArray("items").objects()).distinctBy { it.getString("id") };next = result.first.text("nextCursor");message = if(result.second)"Сохранённый справочник" else null
        }catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;message = "Стили недоступны. Повторите запрос." }finally { busy = false } }
    }
    TextButton({ dialog = true;load() },enabled = !disabled) { Text("Выбрать стиль") }
    if(dialog)AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { dialog = false },title = { Text("Стиль пива") },text = {
        Column {
            OutlinedTextField(query,{ query = it.take(120) },label = { Text("Найти стиль") },modifier = Modifier.fillMaxWidth());TextButton({ load() },enabled = !busy) { Text("Найти") }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());message?.let { Text(it,color = KraPivaColors.Muted) }
            LazyColumn(Modifier.heightIn(max = 300.dp)) { items(rows,key = { it.getString("id") }) { row -> TextButton({ select(row.getString("id"));dialog = false },enabled = !busy) { Text(row.getString("name")) } };if(next != null)item { TextButton({ load(true) },enabled = !busy) { Text("Ещё стили") } } }
        }
    },confirmButton = { TextButton({ dialog = false }) { Text("Закрыть") } })
}
