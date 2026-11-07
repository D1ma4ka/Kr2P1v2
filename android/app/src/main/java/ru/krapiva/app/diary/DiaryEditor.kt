package ru.krapiva.app.diary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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

@Composable internal fun DiaryEditor(state: DiaryState,model: DiaryViewModel) {
    val initial = state.editor ?: return
    val id = initial.getString("draftId")
    var body by rememberSaveable(id) { mutableStateOf(initial.optString("body")) }
    var rating by rememberSaveable(id) { mutableStateOf(initial.text("ratingInput") ?: if(initial.isNull("ratingQuarters")) "" else DiaryNumbers.rating(initial.getInt("ratingQuarters"))) }
    var occurred by rememberSaveable(id) { mutableStateOf(initial.getString("occurredAt")) }
    var serving by rememberSaveable(id) { mutableStateOf(initial.text("servingFormat") ?: "") }
    var venue by rememberSaveable(id) { mutableStateOf(initial.text("venueText") ?: "") }
    var venueId by rememberSaveable(id) { mutableStateOf(initial.text("venueId") ?: "") }
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
        if(validate) { require(body.length <= 5000) { "Не более 5000 символов" };require(serving.length <= 80 && venue.length <= 300) { "Слишком длинное поле подачи или места" };OffsetDateTime.parse(occurred) }
        return JSONObject(initial.toString()).put("body",body).put("ratingQuarters",q ?: JSONObject.NULL).put("ratingInput",rating).put("occurredAt",occurred).put("servingFormat",serving.ifBlank { null } ?: JSONObject.NULL).put("venueId",venueId.ifBlank { null } ?: JSONObject.NULL).put("venueText",if(venueId.isBlank())venue.ifBlank { null } ?: JSONObject.NULL else JSONObject.NULL).put("visibility",visibility).put("tagIds",JSONArray(tags.toList())).put("mediaIds",JSONArray(media.toList())).put("localPhotos",JSONArray(local.toList()))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiaryTitle(if(initial.text("id") == null) "Новый чекин" else "Редактирование чекина")
        Text(initial.optString("positionName"),style = MaterialTheme.typography.titleLarge)
        Text("Позиция выбрана в каталоге. Повторная запись не создаёт новый сорт.",color = KraPivaColors.Muted)
        Text("Изменения не отправлены",color = KraPivaColors.Amber)
        error?.let { Text(it,color = KraPivaColors.Error) }
        OutlinedTextField(rating,{ rating = it },enabled = !state.busy,label = { Text("Оценка 0,25–5 · пусто = без оценки") },modifier = Modifier.fillMaxWidth(),singleLine = true)
        OutlinedTextField(body,{ body = it.take(5000) },enabled = !state.busy,label = { Text("Заметка о пиве") },modifier = Modifier.fillMaxWidth(),minLines = 3)
        OutlinedTextField(occurred,{ occurred = it },enabled = !state.busy,label = { Text("Дата и время ISO (UTC или смещение)") },modifier = Modifier.fillMaxWidth())
        OutlinedTextField(serving,{ serving = it.take(80) },enabled = !state.busy,label = { Text("Подача: кран, бутылка, банка…") },modifier = Modifier.fillMaxWidth())
        if(venueId.isNotBlank()) { Text("Место: ${initial.optString("venueName",venueId)}");TextButton({ venueId = "" }) { Text("Указать место вручную") } }
        else OutlinedTextField(venue,{ venue = it.take(300) },enabled = !state.busy,label = { Text("Место (необязательно)") },modifier = Modifier.fillMaxWidth())
        Text("Видимость",style = MaterialTheme.typography.titleLarge)
        for((value,label)in listOf("private" to "Только я","friends" to "Друзья","public" to "Все")) Row { RadioButton(visibility == value,{ visibility = value },enabled = !state.busy);Text(label,Modifier.padding(top = 12.dp)) }
        Text("Вкусовые теги · ваше впечатление",style = MaterialTheme.typography.titleLarge)
        state.tags.forEach { tag -> val tid = tag.getString("id");Row { Checkbox(tid in tags,{ selected -> tags = if(selected)(tags.toList()+tid).distinct().toTypedArray() else tags.filter { it != tid }.toTypedArray() },enabled = !state.busy);Text(tag.getString("label"),Modifier.padding(top = 12.dp)) } }
        if(state.tags.isEmpty())Text("Справочник тегов недоступен. Можно сохранить без тегов или повторить позже.",color = KraPivaColors.Muted)
        Text("Фото: ${media.size + local.size} из 4",style = MaterialTheme.typography.titleLarge)
        media.forEach { asset -> DiaryPhoto(asset,model.api,state.offline);TextButton({ media = media.filter { it != asset }.toTypedArray() },enabled = !state.busy) { Text("Убрать фото из чекина") } }
        local.forEachIndexed { index,uri -> Row { Text("Выбрано фото ${index+1}",Modifier.weight(1f));TextButton({ local = local.filter { it != uri }.toTypedArray() },enabled = !state.busy) { Text("Убрать выбранное фото") } } }
        TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },enabled = !state.busy && media.size + local.size < 4) { Text("Добавить фото") }
        Text("Только собственные фото или материалы, на которые у вас есть права.",color = KraPivaColors.Muted)
        DiaryButton("Сохранить черновик на устройстве",!state.busy) { error = null;model.saveDraft(data(false)) }
        DiaryButton("Сохранить чекин на сервере",!state.busy) {
            try { error = null;model.publish(data(true)) } catch(e: Exception) { error = if(e is java.time.DateTimeException) "Укажите настоящую дату ISO, например 2026-11-07T12:00:00Z" else e.message ?: "Проверьте поля" }
        }
        Text("Черновик остаётся только на устройстве до подтверждённого сохранения. Выход из аккаунта удаляет локальные черновики.",color = KraPivaColors.Muted)
    }
}
