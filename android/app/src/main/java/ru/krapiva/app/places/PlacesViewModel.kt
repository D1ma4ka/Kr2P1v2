package ru.krapiva.app.places

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects

data class MapCamera(val latitude: Double,val longitude: Double,val zoom: Double,val command: Int=0)
enum class VenueScope(val wireValue: String, val label: String) {
    ALL("all", "Все места"),
    VISITED("visited", "Был здесь"),
    SAVED("saved", "Хочу сходить"),
}
data class PlacesState(val q: String="",val city: String="",val type: String="",val bounds: MapBounds?=null,
    val camera: MapCamera?=null,val map: Boolean=true,val inArea: Boolean=true,
    val scope: VenueScope = VenueScope.ALL, val origin: Coordinates? = null, val deviceLocation: Coordinates? = null, val radius: Int = 5,
    val points: List<JSONObject> = emptyList(),
    val items: List<JSONObject> = emptyList(),val cities: List<JSONObject> = emptyList(),val next: String?=null,val truncated: Boolean=false,
    val selectionBusy: Boolean=false,val selected: JSONObject?=null,val detail: Boolean=false,val busy: Boolean=false,val error: String?=null,val providerError: String?=null)
class PlacesViewModel(val api: AccountApi,private val expired: () -> Unit): ViewModel() {
    private val mutable=MutableStateFlow(PlacesState());val state=mutable.asStateFlow()
    private var cameraCommand = 0
    private var job: Job?=null;private var generation=0
    private var selectionJob: Job?=null;private var selectionGeneration=0
    private var selectionId: String?=null;private var selectionDetail=false;private var lastEntry=-1
    fun enter(request: Int,id: String?) {
        if(lastEntry!=request) {lastEntry=request;selectionId=id;selectionDetail=id!=null}
        if(!selectionDetail)refresh()
        selectionId?.let {select(it,selectionDetail)}
    }
    fun retry() {selectionId?.let {select(it,selectionDetail)}?:refresh()}
    private fun launch(delayMs: Long=0,action: suspend (Int)->Unit) {
        job?.cancel();val token=++generation
        mutable.value=mutable.value.copy(busy=true,error=null)
        job=viewModelScope.launch {
            try { if(delayMs>0)delay(delayMs);mutable.value=mutable.value.copy(busy=true,error=null);action(token) }
            catch(e: Exception) {if(e is CancellationException)throw e;if(token==generation) {
                if(e is ApiFailure && e.status==401)expired()
                mutable.value=mutable.value.copy(error=if(e is ApiFailure)e.message else if(mutable.value.items.isEmpty())"Нет связи. Повторите запрос; данные мест не загружены." else "Не удалось обновить места. Предыдущая загрузка может быть неактуальна.")
            }} finally {if(token==generation)mutable.value=mutable.value.copy(busy=false)}
        }
    }
    private fun query(s: PlacesState,map: Boolean,cursor: String?=null): String {
        val values=linkedMapOf("q" to s.q,"city" to s.city,"type" to s.type)
        if (s.scope != VenueScope.ALL) values["scope"] = s.scope.wireValue
        if(map || s.inArea)values["bbox"]=(s.bounds ?: MapBounds(-180.0,-85.0,180.0,85.0)).query()
        if(!map) { values["limit"]="20";cursor?.let {values["cursor"]=it} }
        return values.filterValues {it.isNotBlank()}.entries.joinToString("&") {"${it.key}=${Uri.encode(it.value)}"}
    }
    fun refresh(more: Boolean=false,debounce: Boolean=false) = launch(if(debounce)1200 else 0) { token ->
        val s=mutable.value
        if (s.origin != null) {
            val query = s.origin.request(s.radius).put("limit", 500)
            if (s.q.isNotBlank()) query.put("q", s.q)
            if (s.type.isNotBlank()) query.put("type", s.type)
            if (s.scope != VenueScope.ALL) query.put("scope", s.scope.wireValue)
            val page = api.json("v1/venues/nearby", "POST", query)
            val rows = page.getJSONArray("items").objects()
            if (token == generation) mutable.value = mutable.value.copy(
                items = rows, points = rows, truncated = page.getBoolean("truncated"), next = null,
            )
        } else if(more) {
            val page=api.json("v1/venues?${query(s,false,s.next)}")
            if(token==generation)mutable.value=mutable.value.copy(items=(s.items+page.getJSONArray("items").objects()).distinctBy {it.getString("id")},next=page.text("nextCursor"))
        } else {
            val page=api.json("v1/venues?${query(s,false)}")
            if(token==generation)mutable.value=mutable.value.copy(items=page.getJSONArray("items").objects(),next=page.text("nextCursor"))
            val map=api.json("v1/venues/map?${query(s,true)}")
            if(token==generation)mutable.value=mutable.value.copy(items=page.getJSONArray("items").objects(),next=page.text("nextCursor"),points=map.getJSONArray("items").objects(),truncated=map.getBoolean("truncated"))
        }
    }
    fun filter(q: String=state.value.q,city: String=state.value.city,type: String=state.value.type,inArea: Boolean=state.value.inArea,scope: VenueScope=state.value.scope) {
        selectionJob?.cancel();selectionGeneration++;selectionId=null
        mutable.value=mutable.value.copy(selectionBusy=false,q=q.take(120),city=city.take(120),type=type,inArea=inArea,scope=scope,items=emptyList(),points=emptyList(),next=null,selected=null,detail=false);refresh(debounce=true)
    }
    fun viewport(bounds: MapBounds,camera: MapCamera) {
        val state = mutable.value
        val changed = state.bounds != bounds
        mutable.value = state.copy(bounds = bounds, camera = camera.copy(command = state.camera?.command ?: 0))
        if(changed && state.origin == null) {
            mutable.value=mutable.value.copy(items=emptyList(),points=emptyList(),next=null)
            refresh(debounce=true)
        }
    }
    fun mode(map: Boolean) {mutable.value=mutable.value.copy(map=map)}
    fun center(lat: Double,lon: Double,zoom: Double=14.0) {
        val state = mutable.value
        mutable.value = state.copy(map = true, camera = MapCamera(lat, lon, zoom, ++cameraCommand))
    }
    fun zoom(delta: Double) {
        val camera = mutable.value.camera ?: return
        center(camera.latitude,camera.longitude,(camera.zoom+delta).coerceIn(1.0,20.0))
    }
    fun radius(radius: Int) {
        if (mutable.value.radius == radius) return
        val state = mutable.value
        if (state.origin == null) mutable.value = state.copy(radius = radius)
        else {
            mutable.value = state.copy(radius = radius, items = emptyList(), points = emptyList(), truncated = false)
            refresh()
        }
    }
    fun locate(coordinates: Coordinates) {
        mutable.value = mutable.value.copy(origin = coordinates, deviceLocation = coordinates)
        filter(city = "")
        center(coordinates.latitude, coordinates.longitude, 13.0)
    }
    fun clearDeviceLocation() {
        val state = mutable.value
        if (state.deviceLocation == null) return
        if (state.origin == state.deviceLocation) {
            mutable.value = state.copy(deviceLocation = null, origin = null, camera = null, bounds = null)
            filter(city = "", inArea = false)
        } else {
            mutable.value = state.copy(deviceLocation = null)
        }
    }
    fun providerError(message: String?) {mutable.value=mutable.value.copy(providerError=message)}
    fun cities(query: String = "") = launch {
        val rows = cityChoices(api, query)
        if (it == generation) mutable.value = mutable.value.copy(cities = rows)
    }
    fun chooseCity(city: JSONObject) {
        val located = !city.isNull("latitude") && !city.isNull("longitude")
        val coordinates = if (located) Coordinates(city.getDouble("latitude"), city.getDouble("longitude")) else null
        mutable.value = mutable.value.copy(origin = coordinates, deviceLocation = null, camera = null, bounds = null)
        filter(city = city.getString("city"), inArea = false)
        if (coordinates != null) center(coordinates.latitude, coordinates.longitude, 11.0) else mode(false)
    }
    fun select(id: String,detail: Boolean=false) {
        selectionJob?.cancel();val token=++selectionGeneration;selectionId=id;selectionDetail=detail
        mutable.value=mutable.value.copy(selected=null,detail=detail,selectionBusy=true,error=null)
        selectionJob=viewModelScope.launch {
            try {val row=api.json("v1/venues/$id");if(token==selectionGeneration)mutable.value=mutable.value.copy(selected=row.apply {
                mutable.value.items.firstOrNull { it.optString("id") == id && it.has("distanceKm") }
                    ?.let { put("distanceKm", it.getDouble("distanceKm")) }
            },detail=detail)}
            catch(e: Exception) {if(e is CancellationException)throw e;if(token==selectionGeneration) {
                if(e is ApiFailure&&e.status==401)expired()
                mutable.value=mutable.value.copy(error=if(e is ApiFailure)e.message else "Место недоступно. Повторите запрос.")
            }} finally {if(token==selectionGeneration)mutable.value=mutable.value.copy(selectionBusy=false)}
        }
    }
    fun toggleSaved() {
        val selected = mutable.value.selected ?: return
        if (mutable.value.selectionBusy) return
        val id = selected.getString("id")
        val desired = !selected.optBoolean("saved")
        val generation = ++selectionGeneration
        mutable.value = mutable.value.copy(selectionBusy = true, error = null)
        selectionJob = viewModelScope.launch {
            try {
                val result = api.json("v1/me/saved-venues/$id", if (desired) "PUT" else "DELETE")
                if (generation == selectionGeneration) {
                    mutable.value = mutable.value.copy(
                        selected = JSONObject(selected.toString()).put("saved", result.getBoolean("saved")),
                    )
                    if (mutable.value.scope == VenueScope.SAVED) refresh()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == selectionGeneration) {
                    if (error is ApiFailure && error.status == 401) expired()
                    mutable.value = mutable.value.copy(error = "Не удалось изменить список мест. Повтори попытку.")
                }
            } finally {
                if (generation == selectionGeneration) mutable.value = mutable.value.copy(selectionBusy = false)
            }
        }
    }

    fun showOnMap(row: JSONObject) {
        if (mutable.value.origin == null) filter(q="",city=row.getString("city"),type="")
        selectionId=row.getString("id");selectionDetail=false
        mutable.value=mutable.value.copy(selected=row,detail=false)
        center(row.getDouble("latitude"),row.getDouble("longitude"),16.0)
    }
    fun details() {selectionDetail=true;mutable.value=mutable.value.copy(detail=true)}
    fun back() {selectionJob?.cancel();selectionGeneration++;selectionId=null;selectionDetail=false;mutable.value=mutable.value.copy(detail=false,selected=null,selectionBusy=false)}
    fun stop() {job?.cancel();selectionJob?.cancel();generation++;selectionGeneration++;mutable.value=mutable.value.copy(busy=false,selectionBusy=false,selected=null)}
}
