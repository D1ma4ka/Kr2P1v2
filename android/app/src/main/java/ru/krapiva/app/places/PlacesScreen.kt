package ru.krapiva.app.places

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
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

@Composable fun PlacesApp(api: AccountApi,userId: String,request: Int,requestedId: String?,onBack: ()->Unit,onCheckin: (JSONObject)->Unit,expired: ()->Unit,bottomBar: @Composable ()->Unit) {
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
    var cities by rememberSaveable {mutableStateOf(false)}
    var locationError by remember {mutableStateOf<String?>(null)}
    var locating by remember {mutableStateOf(false)}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    fun locate() {if(locating)return;locating=true;scope.launch {try {
        val point=withTimeoutOrNull(15_000) {currentLocation(context)}
        if(point==null)locationError="Геопозиция недоступна. Выберите город вручную."
        else {locationError=null;model.filter(city="");model.center(point.latitude,point.longitude,13.0)}
    } catch(e: Exception) {if(e is CancellationException)throw e;locationError="Геопозиция недоступна. Выберите город вручную."}finally {locating=false}}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->if(allowed)locate()else {locationError="Доступ отклонён. Карта работает с ручным выбором города."}}
    BackHandler {if(state.selected!=null)model.back()else onBack()}
    KraPivaBackdrop {Column(Modifier.safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({if(state.selected!=null)model.back()else onBack()}) {Text("Назад")};Text("Места и карта",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))}
        Box(Modifier.fillMaxWidth().height(4.dp)) {if(state.busy||state.selectionBusy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            Text(state.error.orEmpty(),minLines=2,maxLines=2,color=KraPivaColors.Error,modifier=Modifier.weight(1f))
            TextButton(model::retry,enabled=!state.busy&&!state.selectionBusy){Text("Обновить")}
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
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton({cities=true;model.cities()}) {Text(state.city.ifBlank {"Выбрать город"})}
                    TextButton({permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)},enabled=!locating){Text(if(locating)"Определяем…" else "Рядом со мной")}
                }
                locationError?.let {Text(it,color=KraPivaColors.Muted)}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.map,{model.mode(true)},label={Text("Карта")});FilterChip(!state.map,{model.mode(false)},label={Text("Список")})
                    TextButton({filters=!filters}){Text("Фильтры")}
                }
                if(filters) Row {Checkbox(state.inArea,{model.filter(inArea=it)});Text("Список в области карты",modifier=Modifier.padding(top=12.dp))}
            }
            val configured=BuildConfig.MAP_TILES.isNotBlank()&&BuildConfig.MAP_ATTRIBUTION.isNotBlank()
            val fallback=state.providerError!=null||!configured
            if(state.map&&configured)Box(if(fallback)Modifier.fillMaxWidth().height(120.dp)else Modifier.fillMaxWidth().weight(1f)) {
                NativeVenueMap(state,model,Modifier.fillMaxSize().testTag("venue-map"))
                Row(Modifier.padding(8.dp)) {
                    FilledTonalButton({model.zoom(1.0)}){Text("Приблизить")};Spacer(Modifier.width(8.dp));FilledTonalButton({model.zoom(-1.0)}){Text("Отдалить")}
                }
            }
            if(state.map) {
                val handler=LocalUriHandler.current
                if(BuildConfig.MAP_TILES.contains("tile.openstreetmap.org"))TextButton({runCatching {handler.openUri("https://www.openstreetmap.org/copyright")}}){Text("© OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall)}
                else Text(BuildConfig.MAP_ATTRIBUTION,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=16.dp))
                if(fallback)Text(state.providerError?:"Картографическая подложка не настроена. Доступен список мест.",color=KraPivaColors.Error,modifier=Modifier.padding(12.dp))
                Text(when {
                    state.truncated -> "В области больше 500 мест. Приблизьте карту или уточните фильтр."
                    state.points.isEmpty() -> "В области нет загруженных мест с подтверждёнными координатами."
                    else -> "${state.points.size} мест в области. Нажмите на метку или кластер."
                },minLines=2,maxLines=2,modifier=Modifier.padding(horizontal=16.dp),color=KraPivaColors.Muted)
            }
            if(!state.map||fallback)LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(state.items.isEmpty()&&!state.busy)item {Text("Места не найдены. Измените город, фильтр или область. Снимите ограничение области, чтобы увидеть места без координат.")}
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
        AlertDialog(onDismissRequest={cities=false;model.refresh()},title={Text("Город")},text={Column {
            OutlinedTextField(manual,{manual=it.take(120)},label={Text("Введите город вручную")})
            LazyColumn(Modifier.heightIn(max=260.dp)) {items(state.cities,key={it.getString("city")}) {city->TextButton({model.chooseCity(city);cities=false}){Text("${city.getString("city")} · ${city.getInt("count")}")}}}
            Text("Города из подтверждённого справочника. Нет координат — используйте список.")
        }},confirmButton={TextButton({model.filter(city=manual,inArea=false);model.mode(false);cities=false}){Text("Найти")}},dismissButton={TextButton({model.filter(city="");cities=false}){Text("Все города")}})
    }
}
@Composable private fun VenueSummary(row: JSONObject,api: AccountApi,full: Boolean) {
    Text(row.getString("name"),style=MaterialTheme.typography.titleLarge)
    Text(if(row.getString("type")=="bar")"Бар" else "Пивной магазин",color=KraPivaColors.Amber)
    if(row.optBoolean("testOnly"))Text("Тестовая запись · не подтверждает существование реального заведения",color=KraPivaColors.Error)
    Text(listOfNotNull(row.text("city"),row.text("address")).joinToString(" · "))
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
@Suppress("MissingPermission")
private suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
    val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider=manager.getProviders(true).firstOrNull {it==LocationManager.NETWORK_PROVIDER}?:manager.getProviders(true).firstOrNull {it==LocationManager.GPS_PROVIDER}
    if(provider==null) {continuation.resume(null);return@suspendCancellableCoroutine}
    val listener=object: LocationListener {
        override fun onLocationChanged(location: Location) {manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)}
        override fun onProviderDisabled(provider: String) {manager.removeUpdates(this);if(continuation.isActive)continuation.resume(null)}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Android compatibility") override fun onStatusChanged(provider: String?,status: Int,extras: Bundle?) {}
    }
    continuation.invokeOnCancellation {manager.removeUpdates(listener)}
    try {manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())}catch(_: SecurityException){if(continuation.isActive)continuation.resume(null)}
}
