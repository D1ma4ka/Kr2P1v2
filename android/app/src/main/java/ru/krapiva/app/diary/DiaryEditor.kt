package ru.krapiva.app.diary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import ru.krapiva.app.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.KraPivaColors
import java.time.OffsetDateTime

@Composable internal fun DiaryEditor(state: DiaryState,model: DiaryViewModel,onClose: () -> Unit,onChangePosition: () -> Unit) {
    val initial = state.editor ?: return
    val id = initial.getString("draftId")
    var body by rememberSaveable(id) { mutableStateOf(initial.optString("body")) }
    var rating by rememberSaveable(id) { mutableStateOf(initial.text("ratingInput") ?: if(initial.isNull("ratingQuarters")) "" else DiaryNumbers.rating(initial.getInt("ratingQuarters"))) }
    var occurred by rememberSaveable(id) { mutableStateOf(initial.text("occurredInput") ?: DiaryDates.display(initial.getString("occurredAt"))) }
    var serving by rememberSaveable(id) { mutableStateOf(initial.text("servingFormat") ?: "") }
    var venue by rememberSaveable(id) { mutableStateOf(initial.text("venueText") ?: "") }
    var venueId by rememberSaveable(id) { mutableStateOf(initial.text("venueId") ?: "") }
    var venueName by rememberSaveable(id) {mutableStateOf(initial.text("venueName")?:"")}
    var venuePicker by rememberSaveable(id) {mutableStateOf(false)}
    if(venuePicker)ru.krapiva.app.places.VenuePicker(model.api,{row->venueId=row.getString("id");venueName=row.getString("name");venue="";venuePicker=false},{venuePicker=false})
    var visibility by rememberSaveable(id) { mutableStateOf(initial.optString("visibility","private")) }
    var tags by rememberSaveable(id) { mutableStateOf(initial.optJSONArray("tagIds")?.strings()?.toTypedArray() ?: emptyArray<String>()) }
    var media by rememberSaveable(id) { mutableStateOf(initial.optJSONArray("mediaIds")?.strings()?.toTypedArray() ?: emptyArray<String>()) }
    var local by rememberSaveable(id) { mutableStateOf(initial.optJSONArray("localPhotos")?.strings()?.toTypedArray() ?: emptyArray<String>()) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        for(uri in uris) runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if(media.size + (local.toList() + uris.map { it.toString() }).distinct().size > 4) error = "В чекине не более четырёх фото. Удалите лишние."
        else local = (local.toList() + uris.map { it.toString() }).distinct().toTypedArray()
    }
    fun data(validate: Boolean): JSONObject {
        val q = if(validate)DiaryNumbers.quarters(rating) else runCatching { DiaryNumbers.quarters(rating) }.getOrNull()
        if(validate) { require(body.length <= 5000) { "Не более 5000 символов" };require(serving.length <= 80 && venue.length <= 300) { "Слишком длинное поле подачи или места" };DiaryDates.instant(occurred) }
        return JSONObject(initial.toString()).put("body",body).put("ratingQuarters",q ?: JSONObject.NULL).put("ratingInput",rating).put("occurredInput",occurred).put("occurredAt",if(validate) { if(occurred == DiaryDates.display(initial.getString("occurredAt")))initial.getString("occurredAt") else DiaryDates.instant(occurred) } else initial.getString("occurredAt")).put("servingFormat",serving.ifBlank { null } ?: JSONObject.NULL).put("venueName",venueName).put("venueId",venueId.ifBlank { null } ?: JSONObject.NULL).put("venueText",if(venueId.isBlank())venue.ifBlank { null } ?: JSONObject.NULL else JSONObject.NULL).put("visibility",visibility).put("tagIds",JSONArray(tags.toList())).put("mediaIds",JSONArray(media.toList())).put("localPhotos",JSONArray(local.toList()))
    }
    LaunchedEffect(id,body,rating,occurred,serving,venue,venueId,visibility,tags.contentToString(),media.contentToString(),local.contentToString()) {
        model.autosaveDraft(data(false))
    }
    SideEffect { model.editorSnapshot = data(false) }
    var extra by rememberSaveable(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClose,enabled = !state.busy) { Text("Закрыть") }
            Text(if(initial.text("id") == null) "Новый чекин" else "Редактирование чекина",Modifier.weight(1f),style = MaterialTheme.typography.titleLarge)
            TextButton({ model.saveDraft(data(false)) },enabled = !state.busy) { Text("Черновик") }
        }
        ClubCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClubPhoto(model.api,initial.text("imageAssetId"),"Выбранное пиво",Modifier.size(80.dp),cacheOwn = true,offline = state.offline)
                Column(Modifier.weight(1f)) {
                    Text(initial.optString("positionName"),style = MaterialTheme.typography.titleLarge)
                    initial.text("breweryName")?.let { Text(it,color = KraPivaColors.Muted) }
                    initial.text("styleName")?.let { Text(it,color = KraPivaColors.Muted) }
                }
                if(initial.text("id") == null) TextButton(onChangePosition,enabled = !state.busy) { Text("Изменить") }
            }
        }
        DiaryTitle("Твоя оценка")
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rating.ifBlank { "—" },style = MaterialTheme.typography.displaySmall,color = KraPivaColors.Amber)
            Text("/ 5",style = MaterialTheme.typography.headlineSmall,color = KraPivaColors.Muted)
        }
        val quarters = runCatching { DiaryNumbers.quarters(rating) }.getOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ rating = DiaryNumbers.rating(((quarters ?: 2)-1).coerceIn(1,20)) },enabled = !state.busy,contentPadding = PaddingValues(8.dp),modifier = Modifier.sizeIn(minWidth = 48.dp,minHeight = 48.dp)) { Text("−") }
            Slider((quarters ?: 1).toFloat(),{ rating = DiaryNumbers.rating(it.toInt().coerceIn(1,20)) },valueRange = 1f..20f,steps = 18,enabled = !state.busy,modifier = Modifier.weight(1f).testTag("checkin-rating"))
            OutlinedButton({ rating = DiaryNumbers.rating(((quarters ?: 0)+1).coerceIn(1,20)) },enabled = !state.busy,contentPadding = PaddingValues(8.dp),modifier = Modifier.sizeIn(minWidth = 48.dp,minHeight = 48.dp)) { Text("+") }
        }
        TextButton({ rating = "" },enabled = !state.busy,modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Без оценки") }
        OutlinedTextField(rating,{ rating = it },enabled = !state.busy,label = { Text("Оценка 0,25–5 · пусто = без оценки") },modifier = Modifier.fillMaxWidth(),singleLine = true)
        DiaryTitle("Подача")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Кран", "Банка", "Бутылка", "Бокал", "Дегустационный сет").forEach { option ->
                FilterChip(serving == option,{ serving = option },enabled = !state.busy,label = { Text(option) })
            }
        }
        OutlinedTextField(serving,{ serving = it.take(80) },enabled = !state.busy,label = { Text("Свой вариант подачи (необязательно)") },modifier = Modifier.fillMaxWidth())
        DiaryTitle("Вкусы")
        state.tags.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag -> val tid = tag.getString("id")
                    FilterChip(tid in tags,{ tags = if(tid in tags)tags.filter { it != tid }.toTypedArray() else (tags.toList()+tid).toTypedArray() },enabled = !state.busy,label = { Text(tag.getString("label")) },modifier = Modifier.weight(1f))
                }
            }
        }
        if(state.tags.isEmpty()) Text("Справочник вкусов недоступен. Сохранённые теги остаются в черновике.",color = KraPivaColors.Muted)
        DiaryTitle("Впечатления")
        OutlinedTextField(body,{ body = it.take(5000) },enabled = !state.busy,label = { Text("Заметка о пиве") },modifier = Modifier.fillMaxWidth(),minLines = 3)
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            DiaryTitle("Фото");Text("${media.size + local.size} из 4",color = KraPivaColors.Muted)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            media.forEach { asset -> Column(Modifier.width(104.dp)) {
                ClubPhoto(model.api,asset,"Фото чекина",Modifier.size(104.dp),cacheOwn = true,offline = state.offline)
                TextButton({ media = media.filter { it != asset }.toTypedArray() },enabled = !state.busy) { Text("Убрать фото из чекина") }
            } }
            local.forEachIndexed { index,uri -> Column(Modifier.width(104.dp)) {
                SelectedPhoto(uri,index)
                TextButton({ local = local.filter { it != uri }.toTypedArray() },enabled = !state.busy) { Text("Убрать выбранное фото") }
            } }
            OutlinedButton({ runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { error = "Выбор фото недоступен. Попробуйте снова." } },enabled = !state.busy && media.size + local.size < 4,modifier = Modifier.size(104.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { ClubGlyph(ClubIcon.PLUS);Text("Добавить фото") } }
        }
        Text("Только собственные фото или материалы, на которые у вас есть права.",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
        ClubCard {
            Text("Кто увидит запись",style = MaterialTheme.typography.titleLarge)
            for((value,label) in listOf("private" to "Только я","friends" to "Друзья","public" to "Все")) Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(visibility == value,{ visibility = value },enabled = !state.busy);Text(label)
            }
        }
        ClubCard {
            Text("Место",style=MaterialTheme.typography.titleLarge)
            if(venueId.isNotBlank()) {Text(venueName);TextButton({venueId="";venueName=""}){Text("Убрать известное место")}}
            TextButton({venuePicker=true},enabled=!state.busy){Text("Выбрать бар или магазин")}
            if(venueId.isBlank()) {
                OutlinedTextField(venue,{venue=it.take(300)},enabled=!state.busy,label={Text("Место вручную (необязательно)")},modifier=Modifier.fillMaxWidth())
                if(venue.isNotBlank())Text("Не подтверждено · текст не создаёт место на карте",color=KraPivaColors.Amber)
            }
        }
        ClubCard {
            TextButton({ extra = !extra }) { Text(if(extra) "Скрыть дополнительные поля" else "Дополнительно · дата, подача, место и вкусы") }
            if(extra) {
                OutlinedTextField(occurred,{ occurred = it },enabled = !state.busy,label = { Text("Дата и время (ГГГГ-ММ-ДД ЧЧ:ММ)") },modifier = Modifier.fillMaxWidth())
                Text("Часовой пояс: ${java.time.ZoneId.systemDefault().id}",color = KraPivaColors.Muted)

            }
        }
        error?.let { Text(it,color = KraPivaColors.Error) }
        Text("Черновик сохраняется на устройстве автоматически. Публикация — отдельное действие.",color = KraPivaColors.Muted)
        DiaryButton("Проверить пиво и сохранить",!state.busy) {
            try { error = null;model.preparePublish(data(true)) } catch(e: Exception) { error = if(e is java.time.DateTimeException) "Укажите настоящую дату и время, например 2026-11-07 12:00" else e.message ?: "Проверьте поля" }
        }
        DiaryButton("Сохранить черновик на устройстве",!state.busy) { error = null;model.saveDraft(data(false)) }
        Text("Черновик остаётся на устройстве до подтверждённого сохранения. При выходе он сохраняется и будет доступен после входа владельца.",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
    }
}

@Composable private fun SelectedPhoto(uri: String,index: Int) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,uri) {
        value = withContext(Dispatchers.IO) { runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri)).use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
            var sample = 1
            while(bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(Uri.parse(uri)).use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
        }.getOrNull() }
    }
    Box(Modifier.size(104.dp).clip(ClubTokens.ControlShape),contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),"Выбрано фото ${index+1}",Modifier.fillMaxSize(),contentScale = ContentScale.Crop) } ?: Text("Выбрано фото ${index+1}",color = KraPivaColors.Muted)
    }
}
