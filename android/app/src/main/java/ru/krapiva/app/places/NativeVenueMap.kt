package ru.krapiva.app.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.*
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import org.json.JSONObject
import ru.krapiva.app.BuildConfig

internal val mapConfigured get() = BuildConfig.MAPKIT_API_KEY.isNotBlank()
internal const val missingMapKey = "Яндекс Карты не настроены. Доступен список мест."

private object VenueMapKit {
    private var initialized = false

    fun create(context: Context): MapView {
        if (!initialized) {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
            MapKitFactory.initialize(context.applicationContext)
            initialized = true
        }
        return MapView(context)
    }
}

private fun marker(color: Int, label: String): ImageProvider {
    val size = if (label.isEmpty()) 22 else 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, center, paint)
    paint.color = color
    canvas.drawCircle(center, center, center - 2f, paint)
    if (label.isNotEmpty()) {
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (label.length > 2) 16f else 20f
        canvas.drawText(label, center, center - (paint.ascent() + paint.descent()) / 2, paint)
    }
    return ImageProvider.fromBitmap(bitmap)
}

// MapKit keeps weak references to listeners. The Compose view owns this adapter until disposal.
private class VenueOverlay(
    private val map: Map,
    private val onVenue: (String) -> Unit,
) : ClusterListener, ClusterTapListener, MapObjectTapListener {
    private val icons = mutableMapOf<Pair<Int, String>, ImageProvider>()
    private val amber = Color.rgb(255, 182, 60)
    private val venues = map.mapObjects.addClusterizedPlacemarkCollection(WeakReference(this))
    private val device = map.mapObjects.addCollection()
    private var rows: List<JSONObject>? = null
    private var devicePoint: Coordinates? = null
    private var command: Int? = null

    private fun icon(color: Int, label: String = "") = icons.getOrPut(color to label) { marker(color, label) }

    override fun onClusterAdded(cluster: Cluster) {
        cluster.appearance.setIcon(icon(amber, cluster.size.toString()))
        cluster.addClusterTapListener(WeakReference(this))
    }

    override fun onClusterTap(cluster: Cluster): Boolean {
        map.move(CameraPosition(cluster.appearance.geometry, (map.cameraPosition.zoom + 2f).coerceAtMost(20f), 0f, 0f))
        return true
    }

    override fun onMapObjectTap(obj: MapObject, point: Point): Boolean {
        val id = obj.userData as? String ?: return false
        onVenue(id)
        return true
    }

    fun render(state: PlacesState) {
        if (rows !== state.points) {
            rows = state.points
            venues.clear()
            for (row in state.points) {
                val color = when (row.optString("type")) {
                    "brewery" -> Color.rgb(174, 145, 210)
                    "beer_shop" -> Color.rgb(134, 186, 164)
                    else -> amber
                }
                venues.addPlacemark().apply {
                    geometry = Point(row.getDouble("latitude"), row.getDouble("longitude"))
                    setIcon(icon(color, row.optString("matchCount", "")))
                    userData = row.getString("id")
                    addTapListener(WeakReference(this@VenueOverlay))
                }
            }
            venues.clusterPlacemarks(52.0, 19)
        }
        if (devicePoint != state.deviceLocation) {
            devicePoint = state.deviceLocation
            device.clear()
            devicePoint?.let { point ->
                device.addPlacemark().apply {
                    geometry = Point(point.latitude, point.longitude)
                    setIcon(icon(Color.rgb(41, 116, 214)))
                }
            }
        }
        state.camera?.let { camera ->
            if (command != camera.command) {
                command = camera.command
                map.move(CameraPosition(Point(camera.latitude, camera.longitude), camera.zoom.toFloat(), 0f, 0f))
            }
        }
    }
}

@Composable fun NativeVenueMap(state: PlacesState, model: PlacesViewModel, modifier: Modifier = Modifier) {
    NativeVenueMap(state, modifier, model::viewport, { model.select(it) }, model::providerError)
}

@Composable
internal fun NativeVenueMap(
    state: PlacesState,
    modifier: Modifier,
    onViewport: (MapBounds, MapCamera) -> Unit,
    onVenue: (String) -> Unit,
    onError: (String?) -> Unit,
    onPoint: ((Coordinates) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val viewport by rememberUpdatedState(onViewport)
    val select by rememberUpdatedState(onVenue)
    val pick by rememberUpdatedState(onPoint)
    val report by rememberUpdatedState(onError)
    val view = remember(context) {
        try {
            VenueMapKit.create(context)
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
    if (view == null) {
        LaunchedEffect(Unit) { report("Не удалось запустить Яндекс Карты. Доступен список мест.") }
        return
    }
    val map = remember(view) { view.mapWindow.map }
    val overlay = remember(view) { VenueOverlay(map) { select(it) } }
    var loaded by remember(view) { mutableStateOf(false) }
    val connected by remember(context) {
        val network = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
        callbackFlow {
            val listener = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(value: Network) { trySend(true) }
                override fun onLost(value: Network) { trySend(network.activeNetwork != null) }
            }
            network.registerDefaultNetworkCallback(listener)
            trySend(network.activeNetwork != null)
            awaitClose { network.unregisterNetworkCallback(listener) }
        }
    }.collectAsState(initial = true)
    LaunchedEffect(view, connected, loaded) {
        if (!connected) report("Нет подключения к интернету. Загрузка карты недоступна.")
        else {
            if (!loaded) delay(15_000)
            report(if (loaded) null else "Яндекс Карты не загрузились. Проверьте подключение или откройте список.")
        }
    }
    val tiles = remember(view) { MapLoadedListener { loaded = true } }
    DisposableEffect(view) {
        map.isRotateGesturesEnabled = false
        map.isTiltGesturesEnabled = false
        val camera = CameraListener { m, position, _, finished ->
            if (finished && view.width > 0 && view.height > 0) {
                val region = m.visibleRegion
                runCatching {
                    MapBounds.viewport(region.bottomLeft.longitude, region.bottomLeft.latitude,
                        region.topRight.longitude, region.topRight.latitude)
                }.getOrNull()?.let { bounds ->
                    viewport(bounds, MapCamera(position.target.latitude, position.target.longitude, position.zoom.toDouble()))
                }
            }
        }
        val input = object : InputListener {
            override fun onMapTap(map: Map, point: Point) { pick?.invoke(Coordinates(point.latitude, point.longitude)) }
            override fun onMapLongTap(map: Map, point: Point) = Unit
        }
        map.addCameraListener(WeakReference(camera))
        map.addInputListener(WeakReference(input))
        map.setMapLoadedListener(WeakReference(tiles))
        onDispose {
            map.removeCameraListener(WeakReference(camera))
            map.removeInputListener(WeakReference(input))
            view.destroy()
        }
    }
    DisposableEffect(view, lifecycle) {
        var started = false
        fun stop() {
            if (started) {
                view.onStop()
                MapKitFactory.getInstance().onStop()
                started = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    MapKitFactory.getInstance().onStart()
                    view.onStart()
                    started = true
                }
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { overlay.render(state) })
}

@Composable
internal fun OpenInMaps(camera: MapCamera) {
    val handler = LocalUriHandler.current
    TextButton({
        val url = "https://yandex.ru/maps/?ll=${camera.longitude}%2C${camera.latitude}&z=${camera.zoom.toInt()}"
        runCatching { handler.openUri(url) }
    }) { Text("Открыть в Картах") }
}
