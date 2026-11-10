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

data class MapCamera(val latitude: Double=0.0,val longitude: Double=0.0,val zoom: Double=1.0,val command: Int=0)
data class PlacesState(val q: String="",val city: String="",val type: String="",val bounds: MapBounds?=null,
    val camera: MapCamera=MapCamera(),val map: Boolean=true,val inArea: Boolean=true,val points: List<JSONObject> = emptyList(),
    val items: List<JSONObject> = emptyList(),val cities: List<JSONObject> = emptyList(),val next: String?=null,val truncated: Boolean=false,
    val selectionBusy: Boolean=false,val selected: JSONObject?=null,val detail: Boolean=false,val busy: Boolean=false,val error: String?=null,val providerError: String?=null)
class PlacesViewModel(val api: AccountApi,private val expired: () -> Unit): ViewModel() {
    private val mutable=MutableStateFlow(PlacesState());val state=mutable.asStateFlow()
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
        if(map || s.inArea)values["bbox"]=(s.bounds ?: MapBounds(-180.0,-85.0,180.0,85.0)).query()
        if(!map) { values["limit"]="20";cursor?.let {values["cursor"]=it} }
        return values.filterValues {it.isNotBlank()}.entries.joinToString("&") {"${it.key}=${Uri.encode(it.value)}"}
    }
    fun refresh(more: Boolean=false,debounce: Boolean=false) = launch(if(debounce)1200 else 0) { token ->
        val s=mutable.value
        if(more) {
            val page=api.json("v1/venues?${query(s,false,s.next)}")
            if(token==generation)mutable.value=mutable.value.copy(items=(s.items+page.getJSONArray("items").objects()).distinctBy {it.getString("id")},next=page.text("nextCursor"))
        } else {
            val page=api.json("v1/venues?${query(s,false)}")
            if(token==generation)mutable.value=mutable.value.copy(items=page.getJSONArray("items").objects(),next=page.text("nextCursor"))
            val map=api.json("v1/venues/map?${query(s,true)}")
            if(token==generation)mutable.value=mutable.value.copy(items=page.getJSONArray("items").objects(),next=page.text("nextCursor"),points=map.getJSONArray("items").objects(),truncated=map.getBoolean("truncated"))
        }
    }
    fun filter(q: String=state.value.q,city: String=state.value.city,type: String=state.value.type,inArea: Boolean=state.value.inArea) {
        selectionJob?.cancel();selectionGeneration++;selectionId=null
        mutable.value=mutable.value.copy(selectionBusy=false,q=q.take(120),city=city.take(120),type=type,inArea=inArea,items=emptyList(),points=emptyList(),next=null,selected=null,detail=false);refresh(debounce=true)
    }
    fun viewport(bounds: MapBounds,camera: MapCamera) {
        val changed=mutable.value.bounds!=bounds
        mutable.value=mutable.value.copy(bounds=bounds,camera=camera.copy(command=mutable.value.camera.command))
        if(changed) {
            mutable.value=mutable.value.copy(items=emptyList(),points=emptyList(),next=null)
            refresh(debounce=true)
        }
    }
    fun mode(map: Boolean) {mutable.value=mutable.value.copy(map=map)}
    fun center(lat: Double,lon: Double,zoom: Double=14.0) { mutable.value=mutable.value.copy(map=true,camera=MapCamera(lat,lon,zoom,mutable.value.camera.command+1)) }
    fun zoom(delta: Double) {val c=mutable.value.camera;center(c.latitude,c.longitude,(c.zoom+delta).coerceIn(1.0,20.0))}
    fun providerError(message: String?) {mutable.value=mutable.value.copy(providerError=message)}
    fun cities() = launch { token ->val rows=api.json("v1/venue-cities").getJSONArray("items").objects();if(token==generation)mutable.value=mutable.value.copy(cities=rows) }
    fun chooseCity(city: JSONObject) {
        val located=!city.isNull("latitude")&&!city.isNull("longitude")
        filter(city=city.getString("city"),inArea=located)
        if(located)center(city.getDouble("latitude"),city.getDouble("longitude"),11.0)else mode(false)
    }
    fun select(id: String,detail: Boolean=false) {
        selectionJob?.cancel();val token=++selectionGeneration;selectionId=id;selectionDetail=detail
        mutable.value=mutable.value.copy(selected=null,detail=detail,selectionBusy=true,error=null)
        selectionJob=viewModelScope.launch {
            try {val row=api.json("v1/venues/$id");if(token==selectionGeneration)mutable.value=mutable.value.copy(selected=row,detail=detail)}
            catch(e: Exception) {if(e is CancellationException)throw e;if(token==selectionGeneration) {
                if(e is ApiFailure&&e.status==401)expired()
                mutable.value=mutable.value.copy(error=if(e is ApiFailure)e.message else "Место недоступно. Повторите запрос.")
            }} finally {if(token==selectionGeneration)mutable.value=mutable.value.copy(selectionBusy=false)}
        }
    }
    fun showOnMap(row: JSONObject) {
        filter(q="",city=row.getString("city"),type="")
        selectionId=row.getString("id");selectionDetail=false
        mutable.value=mutable.value.copy(selected=row,detail=false)
        center(row.getDouble("latitude"),row.getDouble("longitude"),16.0)
    }
    fun details() {selectionDetail=true;mutable.value=mutable.value.copy(detail=true)}
    fun back() {selectionJob?.cancel();selectionGeneration++;selectionId=null;selectionDetail=false;mutable.value=mutable.value.copy(detail=false,selected=null,selectionBusy=false)}
    fun stop() {job?.cancel();selectionJob?.cancel();generation++;selectionGeneration++;mutable.value=mutable.value.copy(busy=false,selectionBusy=false,selected=null)}
}
