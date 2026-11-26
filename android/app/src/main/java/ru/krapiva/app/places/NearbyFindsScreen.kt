package ru.krapiva.app.places

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaBackdrop
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.diary.objects
import ru.krapiva.app.menu.BarMenuScreen
import ru.krapiva.app.menu.MenuFilter
import ru.krapiva.app.menu.MenuPresetRepository
import ru.krapiva.app.menu.SavedMenuFilter
import ru.krapiva.app.menu.checkinSeed
import ru.krapiva.app.menu.chips
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NearbyFindsScreen(
    api: AccountApi,
    userId: String,
    previous: PlacesState,
    close: () -> Unit,
    expired: () -> Unit,
    openPosition: (String, JSONObject) -> Unit,
    checkin: (JSONObject, JSONObject) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val session = api.sessionIdentity()
    val preference = remember(context) { NearbyPreference(context) }
    var radius by rememberSaveable { mutableIntStateOf(preference.radius) }
    var latitude by rememberSaveable { mutableStateOf(previous.origin?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(previous.origin?.longitude) }
    var fromDevice by rememberSaveable { mutableStateOf(previous.origin != null && previous.origin == previous.deviceLocation) }
    var originName by rememberSaveable { mutableStateOf(previous.city) }
    var mode by rememberSaveable { mutableStateOf(FindMode.UNTRIED) }
    var order by rememberSaveable { mutableStateOf(FindSort.DISTANCE) }
    var releaseHeadline by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var selectedPreset by rememberSaveable { mutableStateOf<String?>(null) }
    var presetOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var map by rememberSaveable { mutableStateOf(true) }
    var activeVenue by rememberSaveable { mutableStateOf<String?>(null) }
    var activeName by rememberSaveable { mutableStateOf("") }
    var selectedVenue by rememberSaveable { mutableStateOf<String?>(null) }
    var options by rememberSaveable { mutableStateOf(false) }
    var radiusPicker by rememberSaveable { mutableStateOf(false) }
    var chooseCity by rememberSaveable { mutableStateOf(false) }
    var pickPoint by rememberSaveable { mutableStateOf(false) }
    var showConditions by rememberSaveable { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationJob by remember { mutableStateOf<Job?>(null) }
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var cameraLatitude by rememberSaveable { mutableStateOf<Double?>(previous.camera?.latitude) }
    var cameraLongitude by rememberSaveable { mutableStateOf<Double?>(previous.camera?.longitude) }
    var cameraZoom by rememberSaveable { mutableStateOf(previous.camera?.zoom ?: 2.0) }
    var cameraCommand by remember { mutableIntStateOf(0) }

    val releaseHeadlines = remember(context) { NewReleaseHeadlines(context) }
    LaunchedEffect(mode, userId) {
        if (mode == FindMode.NEW_RELEASES) {
            if (releaseHeadline == null) releaseHeadline = releaseHeadlines.next()
        } else {
            releaseHeadline = null
        }
    }

    fun useOrigin(point: Coordinates, name: String, device: Boolean) {
        latitude = point.latitude
        longitude = point.longitude
        originName = name
        fromDevice = device
        cameraLatitude = point.latitude.coerceIn(-85.0, 85.0)
        cameraLongitude = point.longitude
        cameraZoom = (14 - nearbyRadii.indexOf(radius)).toDouble()
        cameraCommand++
        selectedVenue = null
        locationError = null
    }

    fun locate() {
        locationJob?.cancel()
        locationError = null
        locationJob = scope.launch {
            try {
                val point = withTimeoutOrNull(15_000) { currentLocation(context) }
                if (point == null) locationError = "Геопозиция недоступна. Можно выбрать город или точку на карте."
                else useOrigin(Coordinates(point.latitude, point.longitude), "", true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                locationError = "Не удалось определить геопозицию. Можно выбрать город вручную."
            } finally {
                locationJob = null
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(context)) locate()
        else locationError = "Геопозиция отключена. Можно выбрать город или точку на карте."
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (fromDevice && !hasLocationPermission(context)) {
                        fromDevice = false
                        latitude = null
                        longitude = null
                        cameraLatitude = null
                        cameraLongitude = null
                        cameraZoom = 2.0
                        activeVenue = null
                    }
                    resumed = true
                }
                Lifecycle.Event.ON_STOP -> {
                    resumed = false
                    locationJob?.cancel()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val presetRepository = remember(api, userId, session) { MenuPresetRepository(api, userId) }
    var presets by remember(session) { mutableStateOf<List<SavedMenuFilter>?>(null) }
    var presetError by remember(session) { mutableStateOf(false) }
    var reloadPresets by remember { mutableIntStateOf(0) }
    val stored = presets?.firstOrNull { it.id == selectedPreset }
    val filter = remember(stored, presetOverride) {
        stored?.let { preset ->
            presetOverride?.let { runCatching { MenuFilter.decode(JSONObject(it)) }.getOrNull() } ?: preset.filter
        }
    }
    LaunchedEffect(presetRepository, resumed, reloadPresets) {
        if (!resumed) return@LaunchedEffect
        try {
            val book = presetRepository.read()
            if (session === api.sessionIdentity()) {
                presets = book.active
                presetError = false
                if (book.active.none { it.id == selectedPreset }) {
                    presetOverride = null
                    if (mode == FindMode.FILTER) activeVenue = null
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (session === api.sessionIdentity()) presetError = true
        }
    }

    val origin = latitude?.let { lat -> longitude?.let { Coordinates(lat, it) } }
        ?.takeIf { !fromDevice || hasLocationPermission(context) }
    val conditions = origin?.takeIf { mode != FindMode.FILTER || filter != null }?.let {
        FindConditions(it, radius, mode, filter.takeIf { mode == FindMode.FILTER })
    }
    val selectedMode = if (mode == FindMode.FILTER) stored?.name ?: mode.title else mode.title
    val query = conditions to order
    var page by remember(query, session) { mutableStateOf<FindPage?>(null) }
    var busy by remember(query, session) { mutableStateOf(false) }
    var error by remember(query, session) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var cursor by remember(query, session) { mutableStateOf<String?>(null) }
    val waitingForPreset = mode == FindMode.FILTER && presets == null && !presetError
    val showingMenu = activeVenue != null && (conditions != null || waitingForPreset)
    val listState = rememberLazyListState()
    LaunchedEffect(query) {
        if (!showingMenu) listState.scrollToItem(0)
    }
    LaunchedEffect(query, session, resumed, showingMenu, refresh) {
        if (!resumed || showingMenu || conditions == null) return@LaunchedEffect
        val next = cursor
        busy = true
        error = null
        try {
            val body = api.json("v1/venues/nearby-finds", "POST", conditions.search(order, next))
            if (session !== api.sessionIdentity()) return@LaunchedEffect
            val loaded = FindPage.read(body)
            page = if (next == null) loaded else page?.append(loaded) ?: loaded
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (session !== api.sessionIdentity()) return@LaunchedEffect
            if (exception is ApiFailure && exception.status == 401) expired()
            if (next == null) page = null
            error = when {
                exception is IOException -> "Нет связи. Проверьте подключение и повторите поиск."
                exception is ApiFailure && exception.status == 422 -> exception.message
                else -> "Не удалось загрузить находки."
            }
        } finally {
            busy = false
            cursor = null
        }
    }
    val back: () -> Unit = {
        when {
            showingMenu -> activeVenue = null
            pickPoint -> pickPoint = false
            selectedVenue != null -> selectedVenue = null
            else -> close()
        }
    }
    BackHandler(onBack = back)
    KraPivaBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val id = activeVenue
            if (id != null && conditions != null) {
                val venue = remember(id, activeName) { JSONObject().put("id", id).put("name", activeName) }
                key(userId, id) {
                    Box(Modifier.weight(1f)) {
                        BarMenuScreen(api, id, activeName, expired, back, { openPosition(it, venue) },
                            { checkin(it.checkinSeed(), venue) }, nearby = conditions)
                    }
                }
            } else if (id != null && waitingForPreset) {
                TextButton(back) { Text("К находкам") }
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
            } else {
                val selected = page?.items?.firstOrNull { it.id == selectedVenue }
                val open: (FindVenue) -> Unit = { venue ->
                    activeName = venue.name
                    activeVenue = venue.id
                }
                val configured = BuildConfig.MAP_TILES.isNotBlank() && BuildConfig.MAP_ATTRIBUTION.isNotBlank()
                val mapVisible = configured && (pickPoint || (map && origin != null))
                val fallback = providerError != null || !configured
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(back) { Text("Назад") }
                    val title = if (mode == FindMode.NEW_RELEASES) releaseHeadline ?: mode.title else "Находки рядом"
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                if (pickPoint) {
                    Text("Нажми на точку карты, чтобы искать от неё.", Modifier.padding(horizontal = 12.dp))
                    if (!configured || providerError != null) Text("Подложка недоступна. Можно выбрать город.",
                        Modifier.padding(horizontal = 12.dp), color = KraPivaColors.Error)
                } else {
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (mode != FindMode.NEW_RELEASES) FilterChip(false, {
                            mode = FindMode.NEW_RELEASES
                            order = FindSort.RELEVANCE
                            selectedVenue = null
                        }, { Text(FindMode.NEW_RELEASES.title) })
                        OutlinedButton({ options = true }, Modifier.testTag("find-conditions")) { Text("Параметры") }
                        TextButton({ radiusPicker = true }, Modifier.testTag("find-radius")) { Text("$radius км") }
                        FilterChip(map, { map = true; selectedVenue = null }, { Text("Карта") })
                        FilterChip(!map, { map = false; selectedVenue = null }, { Text("Список") })
                    }
                    Text(listOfNotNull(
                        origin?.let { if (fromDevice) "Моё местоположение" else originName.ifBlank { "Точка на карте" } },
                        selectedMode.takeUnless { mode == FindMode.NEW_RELEASES },
                    ).joinToString(" · "), Modifier.padding(horizontal = 16.dp), color = KraPivaColors.Muted,
                        style = MaterialTheme.typography.bodySmall)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    locationError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = KraPivaColors.Error) }
                }

                if (mapVisible) {
                    val points = remember(page) { page?.items.orEmpty().asSequence().filter { it.matches > 0 }.take(500).map(FindVenue::point).toList() }
                    val camera = MapCamera(
                        cameraLatitude ?: origin?.latitude?.coerceIn(-85.0,85.0) ?: 35.0,
                        cameraLongitude ?: origin?.longitude ?: 0.0, cameraZoom, cameraCommand,
                    )
                    val state = PlacesState(camera = camera, points = if (pickPoint) emptyList() else points,
                        deviceLocation = origin.takeIf { fromDevice })
                    val mapSize = if (fallback && !pickPoint) Modifier.height(110.dp) else Modifier.weight(1f)
                    NativeVenueMap(state, mapSize.fillMaxWidth().testTag("find-map"),
                        onViewport = { _, position ->
                            cameraLatitude = position.latitude
                            cameraLongitude = position.longitude
                            cameraZoom = position.zoom
                        },
                        onVenue = { selectedVenue = it }, onError = { providerError = it },
                        onPoint = if (pickPoint) {{ point ->
                            useOrigin(point, "", false)
                            map = true
                            pickPoint = false
                        }} else null)
                    FindMapAttribution()
                    if (pickPoint) TextButton({ pickPoint = false; chooseCity = true }) { Text("Выбрать город") }
                }
                if (map && fallback && !pickPoint && origin != null) {
                    Text("Подложка карты недоступна. Находки доступны в списке.",
                        Modifier.padding(horizontal = 16.dp), color = KraPivaColors.Muted)
                }
                if (!pickPoint && (!mapVisible || fallback || page?.count == 0 || error != null)) {
                    LazyColumn(Modifier.weight(1f).testTag("find-list"), state = listState,
                        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (origin == null) item {
                            Text("Выбери город, точку на карте или текущее местоположение.")
                            TextButton({ options = true }) { Text("Выбрать точку поиска") }
                        } else if (mode == FindMode.FILTER && filter == null) item {
                            Text(if (presetError) "Не удалось загрузить сохранённые фильтры." else "Выбери сохранённый фильтр Smart Bar.")
                            TextButton({ options = true }) { Text("Изменить параметры") }
                            if (presetError) TextButton({ reloadPresets++ }) { Text("Повторить загрузку фильтров") }
                        }
                        if (error != null) item {
                            Text(error.orEmpty(), color = KraPivaColors.Error)
                            TextButton({ refresh++ }, enabled = !busy) { Text("Повторить") }
                        }
                        if (page?.count == 0) item {
                            FindEmpty(mode, page?.historySize ?: 0, radius, { radiusPicker = true },
                                { options = true }, { showConditions = true }, filter)
                        }
                        if (mode == FindMode.RECOMMENDED && page?.personalized == false && page?.count != 0) item {
                            Text("Пока мало чекинов для персонального подбора. Показан нейтральный выбор Advisor.",
                                color = KraPivaColors.Muted)
                        }
                        items(page?.items.orEmpty(), key = { it.id }) { venue ->
                            FindVenueCard(venue, { open(venue) })
                        }
                        if (page?.next != null) item {
                            TextButton({ cursor = page?.next; refresh++ }, enabled = !busy) { Text("Загрузить ещё") }
                        }
                    }
                } else if (!pickPoint) {
                    if (mode == FindMode.RECOMMENDED && page?.personalized == false) {
                        Text("Нейтральный подбор Advisor", Modifier.padding(horizontal = 16.dp), color = KraPivaColors.Muted)
                    }
                    if (selected != null) Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                        FindVenueCard(selected, { open(selected) }, { selectedVenue = null })
                    }
                    if (page != null) FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton({ refresh++ }, enabled = !busy) { Text("Обновить") }
                        if (page?.next != null && pointsCount(page) < 500) {
                            TextButton({ cursor = page?.next; refresh++ }, enabled = !busy) { Text("Ещё на карте") }
                        }
                        if (pointsCount(page) >= 500) TextButton({ map = false }) { Text("Все в списке") }
                    }
                }
            }
            bottomBar()
        }
    }
    if (chooseCity) FindCityDialog(api, expired, { chooseCity = false }) { city, point ->
        useOrigin(point, city, false)
        chooseCity = false
    }
    if (radiusPicker) NearbyRadiusDialog(radius, { radiusPicker = false }) {
        radius = it
        preference.select(it)
        radiusPicker = false
        selectedVenue = null
        origin?.let { point -> cameraLatitude = point.latitude.coerceIn(-85.0,85.0); cameraLongitude = point.longitude }
        cameraZoom = (14 - nearbyRadii.indexOf(it)).toDouble()
        cameraCommand++
    }
    if (options) {
        FindOptions(
            mode, order, presets.orEmpty(), selectedPreset, presetError, fromDevice,
            onClose = { options = false },
            onCity = { locationJob?.cancel(); options = false; chooseCity = true },
            onLocation = {
                options = false
                if (hasLocationPermission(context)) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onPoint = {
                locationJob?.cancel()
                options = false
                if (BuildConfig.MAP_TILES.isBlank() || BuildConfig.MAP_ATTRIBUTION.isBlank()) {
                    locationError = "Подложка недоступна. Можно выбрать город."
                    chooseCity = true
                } else pickPoint = true
            },
            onApply = { nextMode, nextSort, nextPreset ->
                mode = nextMode
                order = nextSort
                if (nextPreset != selectedPreset) presetOverride = null
                selectedPreset = nextPreset
                selectedVenue = null
                options = false
            },
        )
    }
    if (showConditions && filter != null) {
        val chips = filter.chips().filterNot { it.label == "В наличии" }
        AlertDialog(onDismissRequest = { showConditions = false }, title = { Text("Убрать условие") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    chips.forEach { chip ->
                        TextButton({
                            presetOverride = chip.cleared.encode().toString()
                            showConditions = false
                        }) { Text(chip.label) }
                    }
                }
            }, confirmButton = { TextButton({ showConditions = false }) { Text("Закрыть") } })
    }
}

private fun pointsCount(page: FindPage?) = page?.items.orEmpty().count { it.matches > 0 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindOptions(
    mode: FindMode,
    order: FindSort,
    presets: List<SavedMenuFilter>,
    presetId: String?,
    presetError: Boolean,
    fromDevice: Boolean,
    onClose: () -> Unit,
    onCity: () -> Unit,
    onLocation: () -> Unit,
    onPoint: () -> Unit,
    onApply: (FindMode, FindSort, String?) -> Unit,
) {
    var selectedMode by rememberSaveable { mutableStateOf(mode) }
    var selectedSort by rememberSaveable { mutableStateOf(order) }
    var selectedPreset by rememberSaveable { mutableStateOf(presetId) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Параметры поиска") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onCity) { Text("Выбрать город") }
                TextButton(onPoint) { Text("Точка на карте") }
                TextButton(onLocation) { Text(if (fromDevice) "Обновить геопозицию" else "Рядом со мной") }
            }
            FindChoice("Что искать", selectedMode.title, FindMode.entries, FindMode::title) {
                if (it == FindMode.NEW_RELEASES && selectedMode != it) selectedSort = FindSort.RELEVANCE
                selectedMode = it
            }
            if (selectedMode == FindMode.FILTER) {
                if (presets.isEmpty()) Text(if (presetError) "Сохранённые фильтры недоступны." else "Именованные фильтры можно сохранить из меню заведения.")
                else FindChoice("Фильтр", presets.firstOrNull { it.id == selectedPreset }?.name ?: "Выбрать",
                    presets, SavedMenuFilter::name) { selectedPreset = it.id }
            }
            val title: (FindSort) -> String = { sort ->
                if (sort == FindSort.DISTANCE && fromDevice) "Ближе ко мне" else sort.title
            }
            FindChoice("Сортировка", title(selectedSort), FindSort.entries, title) { selectedSort = it }
        }
    }, confirmButton = {
        TextButton({ onApply(selectedMode, selectedSort, selectedPreset) },
            enabled = selectedMode != FindMode.FILTER || presets.any { it.id == selectedPreset }) { Text("Применить") }
    }, dismissButton = { TextButton(onClose) { Text("Отмена") } })
}

@Composable
private fun <T> FindChoice(label: String, title: String, choices: List<T>, name: (T) -> String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium)
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(title) }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { value -> DropdownMenuItem(text = { Text(name(value)) }, onClick = {
                expanded = false
                choose(value)
            }) }
        }
    }
}

@Composable
private fun FindCityDialog(api: AccountApi, expired: () -> Unit, close: () -> Unit, select: (String, Coordinates) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, retry) {
        busy = true
        failed = false
        cities = emptyList()
        delay(300)
        try {
            cities = api.json("v1/venue-cities?q=${Uri.encode(query.trim())}").getJSONArray("items").objects()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is ApiFailure && error.status == 401) expired()
            failed = true
        } finally {
            busy = false
        }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Город") }, text = {
        Column {
            OutlinedTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth(),
                label = { Text("Название города") }, singleLine = true)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) TextButton({ retry++ }) { Text("Повторить загрузку городов") }
            if (!busy && !failed && cities.isEmpty()) Text("Город не найден. Можно выбрать точку на карте.")
            LazyColumn(Modifier.heightIn(max = 230.dp)) {
                items(cities, key = { it.getString("city") }) { city ->
                    val name = city.getString("city")
                    val located = !city.isNull("latitude") && !city.isNull("longitude")
                    TextButton({
                        select(name, Coordinates(city.getDouble("latitude"), city.getDouble("longitude")))
                    }, enabled = located) { Text(if (located) name else "$name · нет координат") }
                }
            }
        }
    }, confirmButton = { TextButton(close) { Text("Закрыть") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindEmpty(mode: FindMode, historySize: Int, radius: Int, expand: () -> Unit, other: () -> Unit,
                      conditions: () -> Unit, filter: MenuFilter?) {
    Text(when {
        mode == FindMode.NEW_RELEASES -> "Пока нет новых находок поблизости"
        mode == FindMode.FAVORITE && historySize == 0 -> "В дневнике пока нет данных о любимых стилях."
        else -> "В этом радиусе нет подходящих позиций в свежих меню."
    })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (radius < nearbyRadii.last()) TextButton(expand) { Text("Увеличить радиус") }
        if (filter?.chips()?.any { it.label != "В наличии" } == true) TextButton(conditions) { Text("Убрать условие") }
        TextButton(other) { Text("Изменить режим") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindVenueCard(venue: FindVenue, open: () -> Unit, dismiss: (() -> Unit)? = null) {
    ClubCard {
        Text(venue.name, style = MaterialTheme.typography.titleMedium)
        Text("${venue.city} · ${DiaryNumbers.average(venue.distanceKm.toString())} км", color = KraPivaColors.Muted)
        if (venue.matches > 0) {
            val countLabel = if (venue.newReleaseNames.isEmpty()) "Подходящих позиций" else "Новинки в меню"
            Text("$countLabel · ${venue.matches}", style = MaterialTheme.typography.titleSmall)
            venue.newReleaseNames.forEach { Text(it) }
            if (venue.styles.isNotEmpty()) Text(venue.styles.joinToString(" · "))
            val facts = buildList {
                if (venue.wishlist > 0) add("Из wishlist · ${venue.wishlist}")
                if (venue.untried > 0) add("Непробованных · ${venue.untried}")
                if (venue.favorite > 0) add("Любимых стилей · ${venue.favorite}")
            }
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = KraPivaColors.Muted)
            venue.untriedStyle?.let { (style, count) -> Text("Непробованных в стиле $style · $count", color = KraPivaColors.Muted) }
        } else Text(venue.status.title, color = KraPivaColors.Muted)
        venue.updatedAt?.let {
            val published = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(it)
            Text("Меню опубликовано $published", color = KraPivaColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (venue.matches > 0) TextButton(open) { Text("Посмотреть находки") }
            if (dismiss != null) TextButton(dismiss) { Text("Закрыть") }
        }
    }
}

@Composable
private fun FindMapAttribution() {
    val handler = LocalUriHandler.current
    if (BuildConfig.MAP_TILES.contains("tile.openstreetmap.org")) {
        TextButton({ runCatching { handler.openUri("https://www.openstreetmap.org/copyright") } }) {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
    } else Text(BuildConfig.MAP_ATTRIBUTION, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
}
