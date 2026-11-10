package ru.krapiva.app.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.krapiva.app.account.AccountApi

/** Social media is memory-only. Only explicitly owned content may use the existing encrypted cache. */
@Composable fun ClubPhoto(api: AccountApi, id: String?, label: String, modifier: Modifier,
    cacheOwn: Boolean = false, offline: Boolean = false) {
    var bitmap by remember(api,id,offline) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(api,id,offline) { mutableStateOf(false) }
    LaunchedEffect(api,id,offline) {
        if(id != null) try {
            val bytes = if(cacheOwn) api.photo(id,offline) else api.bytes("v1/media/$id?size=thumbnail")
            bitmap = withContext(Dispatchers.IO) { bytes?.let { BitmapFactory.decodeByteArray(it,0,it.size) } }
            failed = bitmap == null
        } catch(e: Exception) { if(e is CancellationException) throw e;failed = true }
    }
    Box(modifier.clip(ClubTokens.ControlShape).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment = Alignment.Center) {
        if(bitmap != null) Image(bitmap!!.asImageBitmap(),label,Modifier.fillMaxSize(),contentScale = ContentScale.Crop)
        else Column(horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClubGlyph(ClubIcon.GLASS,Modifier.size(28.dp),KraPivaColors.Amber)
            Text(if(id == null) "Без фото" else if(failed) "Фото недоступно" else "Загрузка фото",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
        }
    }
}

@Composable fun ClubHeader(title: String? = null, onSettings: (() -> Unit)? = null, onCheckin: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceBetween) {
            ClubBrand()
            if(onSettings != null) IconButton(onSettings) { ClubGlyph(ClubIcon.SETTINGS,description = "Настройки") }
        }
        if(title != null) Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title,Modifier.weight(1f),style = MaterialTheme.typography.headlineMedium)
            if(onCheckin != null) AmberAction("Чекин",onCheckin)
        }
    }
}
