package ru.krapiva.app.catalog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.krapiva.app.designsystem.KraPivaColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun ScannerScreen(state: CatalogState, model: CatalogViewModel) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf(state.code) }
    var scanVersion by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it; requested = true }
    LaunchedEffect(Unit) { if (!permission && !requested) { requested = true; launcher.launch(Manifest.permission.CAMERA) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogTitle("Сканер штрихкода")
        Text("Наведите камеру на EAN или UPC. QR не является товарным штрихкодом.")
        if (permission && !state.busy && state.lookupState == null && state.error == null) key(scanVersion) {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                CameraPreview(context, { code,kind -> input = if (kind == "gtin") code else ""; model.lookup(code,kind) }, { cameraError = it }, { cameraReady = true })
                ScannerCorners()
            }
        }
        if (cameraReady && cameraError == null && !state.busy && state.lookupState == null && state.error == null) Text("Камера готова · наведите на штрихкод")
        if (!permission) {
            Text("Камера недоступна. Можно ввести штрихкод или вернуться к ручному поиску.")
            TextButton({ launcher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
        }
        cameraError?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        when (state.lookupState) {
            "not_found" -> { Text("Позиция не найдена",style = MaterialTheme.typography.titleLarge); Text("Кода нет в проверенной базе. Это не означает, что продукт непивной."); CatalogAction("Предложить позицию",!state.busy) { model.proposal() } }
            "non_beer" -> { Text("Код подтверждён как непивной"); state.lookupSource?.let { Text("Источник: $it") } }
            "unsupported_qr" -> Text("Это QR-код. Сценарий QR пока не поддерживается; ссылки не открываются.")
        }
        CatalogInput("Введите EAN / UPC / GTIN",input,!state.busy) { input = it.take(14) }
        CatalogAction("Проверить код",!state.busy) { model.lookup(input) }
        TextButton({ model.open(CatalogPage.SCANNER); scanVersion++ },enabled = !state.busy) { Text("Сканировать снова") }
        TextButton({ model.open(CatalogPage.POSITIONS) },enabled = !state.busy) { Text("Ручной поиск") }
    }
}
@Composable private fun ScannerCorners() {
    Canvas(Modifier.fillMaxSize().padding(28.dp)) {
        val length = 28.dp.toPx(); val stroke = 3.dp.toPx()
        for ((x,y,dx,dy) in listOf(listOf(0f,0f,1f,1f),listOf(size.width,0f,-1f,1f),listOf(0f,size.height,1f,-1f),listOf(size.width,size.height,-1f,-1f))) {
            drawLine(KraPivaColors.Cream,Offset(x,y),Offset(x+dx*length,y),stroke)
            drawLine(KraPivaColors.Cream,Offset(x,y),Offset(x,y+dy*length),stroke)
        }
    }
}
/** Copies only the luma plane, respecting row/pixel stride; closes every frame. */
internal fun luma(image: ImageProxy): ByteArray {
    val plane = image.planes[0]; val buffer = plane.buffer.duplicate(); val base = buffer.position()
    return ByteArray(image.width*image.height).also { bytes ->
        for (y in 0 until image.height) for (x in 0 until image.width) bytes[y*image.width+x] = buffer.get(base+y*plane.rowStride+x*plane.pixelStride)
    }
}
@Composable
private fun CameraPreview(context: Context, result: (String,String) -> Unit, failure: (String) -> Unit, ready: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val onResult by rememberUpdatedState(result); val onFailure by rememberUpdatedState(failure); val onReady by rememberUpdatedState(ready)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    AndroidView({ previewView },Modifier.fillMaxSize())
    DisposableEffect(owner,previewView) {
        val executor = Executors.newSingleThreadExecutor(); val consumed = AtomicBoolean(false); val disposed = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val decoder = BarcodeDecoder()
        var cameraState: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>? = null
        val cameraObserver = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { if (it.type == androidx.camera.core.CameraState.Type.OPEN) onReady() }
        var lastAttempt = 0L
        analyzer.setAnalyzer(executor) { frame ->
            try {
                val now = android.os.SystemClock.elapsedRealtime()
                if (!disposed.get() && !consumed.get() && now-lastAttempt >= 180) {
                    lastAttempt = now
                    val data = luma(frame)
                    decoder.decode(data,frame.width,frame.height)?.let { hit ->
                        if (consumed.compareAndSet(false,true)) ContextCompat.getMainExecutor(context).execute { if (!disposed.get()) onResult(hit.code,hit.kind) }
                    }
                }
            } catch (_: Exception) { /* A malformed frame is discarded; subsequent frames remain usable. */ }
            finally { frame.close() }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) consumed.set(false) }
        owner.lifecycle.addObserver(observer)
        providerFuture.addListener({
            if (!disposed.get()) try {
                val camera = providerFuture.get().bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analyzer)
                cameraState = camera.cameraInfo.cameraState
                cameraState?.observe(owner,cameraObserver)
            } catch (_: Exception) { onFailure("Не удалось открыть камеру. Используйте ручной ввод.") }
        },ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true); owner.lifecycle.removeObserver(observer); analyzer.clearAnalyzer(); cameraState?.removeObserver(cameraObserver)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbind(preview,analyzer) }
            executor.shutdown()
        }
    }
}
