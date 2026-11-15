package ru.krapiva.app.offline

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.krapiva.app.designsystem.KraPivaColors

@Composable internal fun SharePreview(title:String,lines:List<String>,warning:String?=null,photo:Bitmap?=null,onClose:()->Unit) {
    val context=LocalContext.current
    var bitmap by remember(title,lines,photo){mutableStateOf<Bitmap?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(title,lines,photo){try{bitmap=withContext(Dispatchers.Default){ShareImages.render(context,title,lines,photo)}}catch(e:Exception){if(e is CancellationException)throw e;error="Не удалось создать изображение. Повторите попытку."}}
    AlertDialog(onDismissRequest=onClose,title={Text("Предпросмотр карточки")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            warning?.let{Text(it,color=KraPivaColors.Amber)}
            bitmap?.let{Image(it.asImageBitmap(),"Точное изображение для отправки",Modifier.fillMaxWidth().aspectRatio(.75f))}
            if(bitmap==null&&error==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=KraPivaColors.Error)}
            Text("Будет отправлено только это изображение через выбранное вами приложение.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton({try{bitmap?.let{ShareImages.share(context,it)}}catch(e:Exception){error="Не удалось открыть отправку. Повторите попытку."}},enabled=bitmap!=null){Text("Поделиться изображением")}},dismissButton={TextButton(onClose){Text("Закрыть")}})
}
