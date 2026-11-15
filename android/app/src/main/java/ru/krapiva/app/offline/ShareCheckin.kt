package ru.krapiva.app.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.diary.DiaryDates

@Composable fun ShareCheckin(api:AccountApi,data:JSONObject) {
    val id=data.text("id") ?: return
    if(data.text("userId")!=api.ownerId())return
    var opened by rememberSaveable(id){mutableStateOf(false)}
    var preview by remember(id){mutableStateOf(false)}
    var includeNote by rememberSaveable(id){mutableStateOf(false)}
    var includePhoto by rememberSaveable(id){mutableStateOf(true)}
    var photo by remember(id){mutableStateOf<Bitmap?>(null)}
    var photoLoaded by remember(id){mutableStateOf(false)}
    LaunchedEffect(id,opened){
        if(opened&&!photoLoaded){
            try {
                val asset=data.optJSONArray("mediaIds")?.optString(0)?.takeIf{it.isNotBlank()}
                photo=if(asset==null)null else withContext(Dispatchers.IO){api.photo(asset,true)?.let{BitmapFactory.decodeByteArray(it,0,it.size)}}
            } catch(e:Exception){if(e is CancellationException)throw e}
            finally{photoLoaded=true}
        }
    }
    TextButton({opened=true}){Text("Поделиться чекином")}
    if(opened&&!preview)AlertDialog(onDismissRequest={opened=false},title={Text("Карточка моего чекина")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(data.optString("visibility")!="public")Text("Эта запись ограниченного доступа. Отправка изображения передаст выбранные сведения получателям вне KraPiva. Проверьте предпросмотр.",color=KraPivaColors.Amber)
            Row {Checkbox(includeNote,{includeNote=it});Text("Включить короткую заметку")}
            Row {Checkbox(includePhoto,{includePhoto=it},enabled=photo!=null);Text("Включить доступное фото")}
            Text(if(!photoLoaded)"Проверяем сохранённое фото…" else if(photo==null)"Фото на устройстве недоступно. Карточка будет без фото." else "Используется первое доступное фото.",color=KraPivaColors.Muted)
            Text("Ссылка на публикацию не создаётся. Видимость исходной записи сохраняется.")
        }
    },confirmButton={TextButton({preview=true},enabled=photoLoaded){Text("Показать изображение")}},dismissButton={TextButton({opened=false}){Text("Отмена")}})
    if(opened&&preview) {
        val lines=listOfNotNull(data.text("breweryName"),if(data.isNull("ratingQuarters"))"Без оценки" else "Моя оценка: ${DiaryNumbers.rating(data.getInt("ratingQuarters"))} / 5",
            data.text("occurredAt")?.let{DiaryDates.display(it)},if(includeNote)data.text("body")?.take(220) else null)
        SharePreview(data.optString("positionName"),lines,
            warning=if(data.optString("visibility")!="public")"В изображении есть сведения из записи ограниченного доступа. Отправляйте только выбранным получателям." else null,
            photo=if(includePhoto)photo else null,onClose={preview=false;opened=false})
    }
}
