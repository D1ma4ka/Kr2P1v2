package ru.krapiva.app.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.moderation.ReportAction
import kotlin.coroutines.resume

@Composable fun PlacesApp(api: AccountApi,userId: String,request: Int,requestedId: String?,onBack: ()->Unit,onCheckin: (JSONObject)->Unit,expired: ()->Unit,chooseCityOnEntry: Boolean = false,bottomBar: @Composable ()->Unit) {
    val model=viewModel<PlacesViewModel>(key="places-$userId",factory=object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=PlacesViewModel(api,expired) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,model,request) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_START)model.enter(request,requestedId)else if(event==Lifecycle.Event.ON_STOP)model.stop()}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))model.enter(request,requestedId)
        onDispose {owner.lifecycle.removeObserver(observer);model.stop()}
    }
    var filters by rememberSaveable {mutableStateOf(false)}
    var cities by rememberSaveable(request) {mutableStateOf(chooseCityOnEntry)}
    var radiusPicker by rememberSaveable { mutableStateOf(false) }
    var locationError by remember {mutableStateOf<String?>(null)}
    var locating by remember {mutableStateOf(false)}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nearby = remember(context) { NearbyPreference(context) }
    var locationJob by remember { mutableStateOf<Job?>(null) }

    fun locate() {
        if (locating) return
        locating = true
        locationError = null
        locationJob = scope.launch {
            try {
                val point = withTimeoutOrNull(15_000) { currentLocation(context) }
                if (point == null) {
                    locationError = "Геопозиция недоступна. Проверь настройки Android или выбери город."
                } else {
                    model.locate(Coordinates(point.latitude, point.longitude))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                locationError = "Геопозиция недоступна. Попробуй снова или выбери город."
            } finally {
                locating = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(context)) locate()
        else locationError = "Доступ к геопозиции отключён. Можно выбрать город вручную."
    }
    fun useCurrentLocation() {
        if (hasLocationPermission(context)) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(nearby.radius) { model.radius(nearby.radius) }
    LaunchedEffect(request) {
        if (!chooseCityOnEntry && requestedId == null && state.city.isBlank() && hasLocationPermission(context)) locate()
    }
    DisposableEffect(owner, locationJob) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!hasLocationPermission(context)) model.clearDeviceLocation()
                Lifecycle.Event.ON_STOP -> locationJob?.cancel()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler {if(state.selected!=null)model.back()else onBack()}
    KraPivaBackdrop {Column(Modifier.safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({if(state.selected!=null)model.back()else onBack()}) {Text("Назад")};Text("Места и карта",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))}
        Box(Modifier.fillMaxWidth().height(4.dp)) {if(state.busy||state.selectionBusy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            state.error?.let { Text(it,color=KraPivaColors.Error,modifier=Modifier.weight(1f)) }
            if (state.error != null) TextButton(model::retry,enabled=!state.busy&&!state.selectionBusy){Text("Повторить")}
        }
        val selected=state.selected
        if(state.detail && selected!=null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                VenueSummary(selected,api,true)
                if(!selected.isNull("latitude"))AmberAction("На карте",{model.showOnMap(selected)})
                else Text("Координаты не подтверждены. На карту место не наносится.",color=KraPivaColors.Muted)
                AmberAction("Чекин в этом месте",{onCheckin(selected)})
                ReportAction(api,"venue",selected.getString("id"),expired)
            }
        } else {
            Column(Modifier.padding(horizontal=16.dp)) {
                if(filters) {
                OutlinedTextField(state.q,{model.filter(q=it)},label={Text("Название или адрес")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    for((value,label) in listOf("" to "Все","bar" to "Бары","beer_shop" to "Магазины"))FilterChip(state.type==value,{model.filter(type=value)},label={Text(label)})
                }
                }
                if (state.camera == null && !locating && locationError == null) {
                    Text("Геопозиция нужна, чтобы открыть карту рядом с тобой и показать расстояния.", color = KraPivaColors.Muted)
                }
                locationError?.let { Text(it, color = KraPivaColors.Muted) }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ locationJob?.cancel(); cities = true }, Modifier.weight(1f)) {
                            Text(state.city.ifBlank { "Выбрать город" })
                        }
                        TextButton(::useCurrentLocation, Modifier.weight(1f), enabled = !locating) {
                            Text(if (locating) "Определяем…" else if (state.deviceLocation != null) "На мою позицию" else "Рядом со мной")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.map && state.camera != null, { model.mode(true) }, label = { Text("Карта") }, enabled = state.camera != null)
                        FilterChip(!state.map || state.camera == null, { model.mode(false) }, label = { Text("Список") })
                        TextButton({ filters = !filters }) { Text("Фильтры") }
                    }
                    if (state.origin != null) {
                        TextButton({ radiusPicker = true }, Modifier.heightIn(min = 48.dp)) { Text("Радиус · ${nearby.radius} км") }
                    } else if (filters) {
                        Row {
                            Checkbox(state.inArea, { model.filter(inArea = it) }, enabled = state.camera != null)
                            Text("Список в области карты", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            val configured=BuildConfig.MAP_TILES.isNotBlank()&&BuildConfig.MAP_ATTRIBUTION.isNotBlank()
            val fallback=state.providerError!=null||!configured
            val showMap = state.map && state.camera != null
            if(showMap&&configured)Box(if(fallback)Modifier.fillMaxWidth().height(120.dp)else Modifier.fillMaxWidth().weight(1f)) {
                NativeVenueMap(state,model,Modifier.fillMaxSize().testTag("venue-map"))
                Row(Modifier.padding(8.dp)) {
                    FilledTonalButton({model.zoom(1.0)}){Text("Приблизить")};Spacer(Modifier.width(8.dp));FilledTonalButton({model.zoom(-1.0)}){Text("Отдалить")}
                }
            }
            if(showMap) {
                val handler=LocalUriHandler.current
                if(BuildConfig.MAP_TILES.contains("tile.openstreetmap.org"))TextButton({runCatching {handler.openUri("https://www.openstreetmap.org/copyright")}}){Text("© OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall)}
                else Text(BuildConfig.MAP_ATTRIBUTION,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=16.dp))
                if(fallback)Text(state.providerError?:"Картографическая подложка не настроена. Доступен список мест.",color=KraPivaColors.Error,modifier=Modifier.padding(12.dp))
                Text(when {
                    state.truncated -> "Показаны первые 500 мест. Уточни фильтр."
                    state.points.isEmpty() -> "Здесь нет мест с подтверждёнными координатами."
                    state.origin != null -> "В радиусе ${state.radius} км · ${state.points.size} мест"
                    else -> "${state.points.size} мест в области. Нажми на метку или кластер."
                },minLines=2,maxLines=2,modifier=Modifier.padding(horizontal=16.dp),color=KraPivaColors.Muted)
            }
            if(!showMap||fallback)LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(state.items.isEmpty()&&!state.busy)item {Text("Места не найдены. Попробуй другой город, фильтр или радиус.")}
                if (state.truncated && !showMap) item { Text("Показаны первые 500 мест. Уточни фильтр.") }
                items(state.items,key={it.getString("id")}) {row->ClubCard {VenueSummary(row,api,false);TextButton({model.select(row.getString("id"),true)}){Text("Открыть место")};if(!row.isNull("latitude"))TextButton({model.showOnMap(row)}){Text("Показать на карте")}}}
                if(state.next!=null)item {TextButton({model.refresh(true)},enabled=!state.busy){Text("Загрузить ещё")}}
            }
            if(!state.detail&&selected!=null)ClubCard(Modifier.padding(12.dp)) {
                Text(selected.getString("name"),style=MaterialTheme.typography.titleLarge)
                if(selected.optBoolean("testOnly"))Text("Только тестовая запись",color=KraPivaColors.Error)
                Row {TextButton(model::details){Text("Подробнее")};TextButton({onCheckin(selected)}){Text("Чекин здесь")};TextButton(model::back){Text("Закрыть")}}
            }
        }
        bottomBar()
    }}
    if(cities) {
        var manual by rememberSaveable {mutableStateOf(state.city)}
        LaunchedEffect(manual) {
            delay(250)
            model.cities(manual.trim())
        }
        AlertDialog(onDismissRequest={cities=false;model.refresh()},title={Text("Город")},text={Column {
            OutlinedTextField(manual,{manual=it.take(120)},label={Text("Введите город вручную")})
            LazyColumn(Modifier.heightIn(max=260.dp)) {items(state.cities,key={it.getString("city")}) {city->TextButton({model.chooseCity(city);cities=false}){Text("${city.getString("city")} · ${city.getInt("count")}")}}}
            Text("Города из справочника заведений")
        }},confirmButton={TextButton({
            val city = state.cities.firstOrNull { it.getString("city").equals(manual.trim(), ignoreCase = true) }
            if (city != null) model.chooseCity(city)
            else model.chooseCity(JSONObject().put("city", manual.trim()))
            cities = false
        }){Text("Выбрать")}},dismissButton={TextButton({model.chooseCity(JSONObject().put("city",""));cities=false}){Text("Все города")}})
    }
    if (radiusPicker) {
        NearbyRadiusDialog(nearby.radius, { radiusPicker = false }) { radius ->
            nearby.select(radius)
            radiusPicker = false
        }
    }
}

@Composable
internal fun NearbyRadiusDialog(current: Int, onClose: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Радиус поиска") },
        text = {
            Column {
                for (radius in nearbyRadii) {
                    TextButton({ onSelect(radius) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (radius == current) "$radius км · выбран" else "$radius км")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Закрыть") } },
    )
}

@Composable private fun VenueSummary(row: JSONObject,api: AccountApi,full: Boolean) {
    Text(row.getString("name"),style=MaterialTheme.typography.titleLarge)
    Text(if(row.getString("type")=="bar")"Бар" else "Пивной магазин",color=KraPivaColors.Amber)
    if(row.optBoolean("testOnly"))Text("Тестовая запись · не подтверждает существование реального заведения",color=KraPivaColors.Error)
    Text(listOfNotNull(row.text("city"),row.text("address")).joinToString(" · "))
    if (row.has("distanceKm")) Text("${DiaryNumbers.average(row.text("distanceKm"))} км", color = KraPivaColors.Muted)
    if(full) {
        if(row.text("imageAssetId")!=null)ClubPhoto(api,row.text("imageAssetId"),"Фото заведения",Modifier.fillMaxWidth().height(180.dp))
        else Text("Фото не предоставлено",color=KraPivaColors.Muted)
        Text("Источник: ${row.getString("source")}",style=MaterialTheme.typography.bodySmall)
        row.text("coordinatesSource")?.let {Text("Координаты: $it",style=MaterialTheme.typography.bodySmall)}
        row.text("imageSource")?.let {Text("Фото: $it",style=MaterialTheme.typography.bodySmall)}
        row.optJSONObject("statistics")?.let {s->Text("По доступным вам записям: ${s.getInt("totalCheckins")} чекинов · ${s.getInt("ratedCheckins")} оценок")
            Text(if(s.isNull("averageRating"))"Нет оценок" else "Средняя оценка: ${DiaryNumbers.average(s.text("averageRating"))}")}
    }
}
internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Suppress("MissingPermission")
internal suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
    val manager = context.getSystemService(LocationManager::class.java)
    val providers = manager.getProviders(true).filter {
        it == LocationManager.NETWORK_PROVIDER || it == LocationManager.GPS_PROVIDER || it == "fused"
    }
    val recent = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { point ->
        SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos in 0..30_000_000_000L
    }.minByOrNull { it.accuracy }
    if (recent != null) {
        continuation.resume(recent)
        return@suspendCancellableCoroutine
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            manager.removeUpdates(this)
            if (continuation.isActive) continuation.resume(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Android compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    val registered = providers.count { provider ->
        runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }.isSuccess
    }
    if (registered == 0 && continuation.isActive) continuation.resume(null)
}
