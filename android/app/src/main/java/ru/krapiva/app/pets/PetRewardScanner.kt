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

@Composable
internal fun PetRewardScanner(enabled:Boolean,onCode:(String)->Unit) {
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
        CameraPreview(context,{code,_->camera=false;onCode(code)},{error=it;camera=false},{},
            accept={code,kind->kind=="qr" && code.matches(Regex("krapiva:reward:[A-Za-z0-9_-]{16,180}"))})
    }
    error?.let { Text(it) }
}
