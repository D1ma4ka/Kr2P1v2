package ru.krapiva.app.places

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import ru.krapiva.app.BuildConfig
import java.io.File

/** Dedicated unauthenticated tile client; neither API tokens nor device coordinates are attached. */
private object TileNetwork {
    val healthy=MutableStateFlow(true)
    private var installed=false
    @Synchronized fun install(context: android.content.Context) {
        if(installed)return
        val client=OkHttpClient.Builder().cache(Cache(File(context.cacheDir,"map-tiles"),32L*1024*1024))
            .addInterceptor { chain ->
                try {
                    val response=chain.proceed(chain.request().newBuilder().header("User-Agent","Krapiva/${BuildConfig.VERSION_NAME} (Android; https://github.com/D1ma4ka/Kr2P1v2)").build())
                    healthy.value=response.isSuccessful;response
                } catch(e: java.io.IOException) {healthy.value=false;throw e}
            }.build()
        HttpRequestUtil.setOkHttpClient(client)
        HttpRequestUtil.setLogEnabled(false);HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        installed=true
    }
}
private fun clusterIcon(count: Int): Bitmap {
    val b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);val c=Canvas(b)
    val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(255,182,60)};c.drawCircle(32f,32f,29f,p)
    p.color=Color.rgb(20,24,21);p.textSize=if(count>=100)23f else 28f;p.textAlign=Paint.Align.CENTER;p.isFakeBoldText=true
    c.drawText(count.toString(),32f,32f-(p.ascent()+p.descent())/2,p);return b
}
private fun mapStyle(): String = JSONObject().put("version",8).put("sources",JSONObject().put("tiles",JSONObject()
    .put("type","raster").put("tiles",JSONArray().put(BuildConfig.MAP_TILES)).put("tileSize",256).put("maxzoom",19)
    .put("attribution",BuildConfig.MAP_ATTRIBUTION)))
    .put("layers",JSONArray().put(JSONObject().put("id","base").put("type","raster").put("source","tiles"))).toString()

@Composable fun NativeVenueMap(state: PlacesState,model: PlacesViewModel,modifier: Modifier=Modifier) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val latest by rememberUpdatedState(state)
    val healthy by TileNetwork.healthy.collectAsState()
    LaunchedEffect(healthy) {model.providerError(if(healthy)null else "Подложка карты недоступна. Откройте список мест или повторите загрузку.")}
    val view=remember {
        MapLibre.getInstance(context.applicationContext);TileNetwork.install(context.applicationContext)
        MapView(context).apply {contentDescription="Интерактивная карта мест. Для выбора без жестов откройте список.";onCreate(null)}
    }
    var map by remember {mutableStateOf<MapLibreMap?>(null)}
    var ready by remember {mutableStateOf(false)}
    var renderedPoints by remember {mutableStateOf<List<JSONObject>?>(null)}
    var command by remember {mutableIntStateOf(-1)}
    DisposableEffect(view,owner) {
        var started=false;var resumed=false
        fun start(){if(!started){view.onStart();started=true}}
        fun resume(){start();if(!resumed){view.onResume();resumed=true}}
        fun pause(){if(resumed){view.onPause();resumed=false}}
        fun stop(){pause();if(started){view.onStop();started=false}}
        val observer=LifecycleEventObserver {_,event->when(event){Lifecycle.Event.ON_START->start();Lifecycle.Event.ON_RESUME->resume();Lifecycle.Event.ON_PAUSE->pause();Lifecycle.Event.ON_STOP->stop();else->Unit}}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))start()
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))resume()
        onDispose {owner.lifecycle.removeObserver(observer);stop();view.onDestroy();map=null;ready=false}
    }
    AndroidView(factory={
        view.addOnDidFailLoadingMapListener { model.providerError("Не удалось загрузить карту. Доступен список мест.") }
        view.addOnStyleImageMissingListener { id ->
            if(id.startsWith("count-"))id.removePrefix("count-").toIntOrNull()?.takeIf {it in 2..500}?.let {count->map?.style?.addImage(id,clusterIcon(count))}
        }
        view.getMapAsync { m ->
            map=m;m.setPrefetchZoomDelta(0);m.uiSettings.isCompassEnabled=true;m.uiSettings.isRotateGesturesEnabled=false;m.uiSettings.isTiltGesturesEnabled=false
            val initial=latest.camera
            m.cameraPosition=CameraPosition.Builder().target(LatLng(initial.latitude,initial.longitude)).zoom(initial.zoom).build();command=initial.command
            m.setStyle(Style.Builder().fromJson(mapStyle())) { style ->
                style.addSource(GeoJsonSource("venues",FeatureCollection.fromFeatures(emptyList()),GeoJsonOptions().withCluster(true).withClusterRadius(52).withClusterMaxZoom(18)))
                style.addLayer(CircleLayer("points","venues").withFilter(not(has("point_count"))).withProperties(
                    circleRadius(10f),circleColor(match(get("type"),literal("beer_shop"),literal("#86baa4"),literal("#ffb63c"))),circleStrokeColor("#15231e"),circleStrokeWidth(2f)))
                style.addLayer(CircleLayer("clusters","venues").withFilter(has("point_count")).withProperties(circleRadius(22f),circleColor("#ffb63c")))
                // Numeric labels are generated locally on demand. No third-party glyph service.
                style.addLayer(SymbolLayer("counts","venues").withFilter(has("point_count")).withProperties(iconImage(concat(literal("count-"),toString(get("point_count")))),iconSize(.75f),iconAllowOverlap(true)))
                ready=true
            }
            m.addOnCameraIdleListener {
                if(ready) {
                    val b=m.projection.visibleRegion.latLngBounds;val c=m.cameraPosition
                    runCatching {MapBounds.viewport(b.longitudeWest,b.latitudeSouth,b.longitudeEast,b.latitudeNorth)}.onSuccess {model.viewport(it,MapCamera(c.target?.latitude?:0.0,c.target?.longitude?:0.0,c.zoom))}
                }
            }
            m.addOnMapClickListener { location ->
                val features=m.queryRenderedFeatures(m.projection.toScreenLocation(location),"clusters","points")
                val feature=features.firstOrNull()
                if(feature!=null) {
                    if(feature.hasProperty("cluster_id")) {
                        val source=m.style?.getSourceAs<GeoJsonSource>("venues")
                        val point=feature.geometry() as? Point
                        if(source!=null&&point!=null)m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude(),point.longitude()),source.getClusterExpansionZoom(feature).toDouble()))
                    } else model.select(feature.getStringProperty("id"))
                }
                feature!=null
            }
        }
        view
    },modifier=modifier,update={
        if(ready) {
            if(renderedPoints !== state.points) {renderedPoints=state.points
            map?.style?.getSourceAs<GeoJsonSource>("venues")?.setGeoJson(FeatureCollection.fromFeatures(state.points.map {p->
                Feature.fromGeometry(Point.fromLngLat(p.getDouble("longitude"),p.getDouble("latitude"))).apply {addStringProperty("id",p.getString("id"));addStringProperty("type",p.getString("type"))}
            }))}
            if(command!=state.camera.command) {
                command=state.camera.command
                val c=state.camera
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.latitude,c.longitude),c.zoom))
            }
        }
    })
}
