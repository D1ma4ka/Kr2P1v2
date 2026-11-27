package ru.krapiva.app.pets

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.krapiva.app.catalog.CameraPreview

private val petRewardPattern = Regex("krapiva:reward:[A-Za-z0-9_-]{16,180}")

internal fun isPetRewardCode(code: String): Boolean = petRewardPattern.matches(code)

internal fun acceptsPetRewardScan(code: String, kind: String): Boolean = kind == "qr" && isPetRewardCode(code)

internal typealias PetRewardCamera = @Composable (
    result: (String, String) -> Unit,
    failure: (String) -> Unit,
    accept: (String, String) -> Boolean,
) -> Unit

@Composable
internal fun DevicePetRewardCamera(
    result: (String, String) -> Unit,
    failure: (String) -> Unit,
    accept: (String, String) -> Boolean,
) {
    CameraPreview(LocalContext.current, result, failure, ready = {}, accept = accept)
}

@Composable
internal fun PetRewardScanner(enabled:Boolean, cameraPreview:PetRewardCamera, onCode:(String)->Unit) {
    val context=LocalContext.current
    var camera by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val launch=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        camera=it
        error=if(it)null else "Камера недоступна. Введите код вручную."
    }
    Button({
        if(camera) camera=false
        else if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) camera=true
        else launch.launch(Manifest.permission.CAMERA)
    },enabled=enabled) {Text(if(camera)"Закрыть камеру" else "Сканировать QR")}
    if(camera && enabled) Box(Modifier.fillMaxWidth().height(200.dp)) {
        cameraPreview({code,_->camera=false;onCode(code)},{error=it;camera=false},::acceptsPetRewardScan)
    }
    error?.let { Text(it) }
}
