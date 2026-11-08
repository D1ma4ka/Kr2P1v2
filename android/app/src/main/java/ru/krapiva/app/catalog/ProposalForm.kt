package ru.krapiva.app.catalog

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
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun ProposalForm(state: CatalogState, model: CatalogViewModel) {
    val existing = state.proposal
    var draft by rememberSaveable(existing?.optString("id")) { mutableStateOf((existing?.getJSONObject("proposedData") ?: JSONObject().put("gtin",state.code)).toString()) }
    val requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val data = JSONObject(draft)
    fun change(field: String, value: Any?) { draft = JSONObject(draft).put(field,value ?: JSONObject.NULL).toString() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri.toString() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogTitle(if (existing == null) "Предложить позицию" else "Исправить предложение")
        Text("Укажите сведения с этикетки или подтверждённого источника. Заявка появится в каталоге после проверки. Неизвестные поля оставьте пустыми.")
        for ((field,label,max) in listOf(
            Triple("name","Название *",200),Triple("breweryName","Пивоварня *",200),Triple("breweryCountry","Страна пивоварни",100),
            Triple("breweryCity","Город пивоварни",120),Triple("brewerySource","Источник сведений о пивоварне",2000),
            Triple("brand","Бренд",200),Triple("styleName","Стиль",120),Triple("abv","Крепость, %",6),Triple("ibu","IBU",7),
            Triple("packaging","Упаковка",80),Triple("volumeMl","Объём, мл",6),Triple("edition","Выпуск",200),Triple("variant","Вариант",200),
            Triple("description","Описание",5000),Triple("source","Источник сведений *",2000),Triple("gtin","EAN / UPC / GTIN",14))) {
            CatalogInput(label,data.text(field) ?: "", !state.busy) { change(field,it.take(max)) }
            if (field == "gtin") Text("Вручную: EAN-8/13, UPC-A или GTIN-14. UPC-E распознаёт сканер.")
        }
        TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },enabled = !state.busy) { Text(if (photo == null && data.text("imageAssetId") == null) "Добавить своё фото" else "Заменить фото") }
        if (photo != null || data.text("imageAssetId") != null) {
            Text("Фото выбрано; публикация после проверки прав.")
            CatalogInput("Источник фото / подтверждение авторства",data.text("imageSource") ?: "",!state.busy) { change("imageSource",it.take(2000)) }
            Row { Checkbox(data.optBoolean("rightsConfirmed"),{ change("rightsConfirmed",it) },enabled = !state.busy); Text("У меня есть право публиковать это фото",Modifier.weight(1f).padding(top = 12.dp)) }
            TextButton({ photo = null; change("imageAssetId",null); change("imageSource",null); change("rightsConfirmed",false) },enabled = !state.busy) { Text("Убрать фото из заявки") }
        }
        localError?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        CatalogAction("Отправить на проверку",!state.busy) {
            val value = JSONObject(draft)
            localError = when {
                listOf("name","breweryName","source").any { value.text(it) == null } -> "Заполните название, пивоварню и источник"
                value.text("gtin") != null && Gtin.normalize(value.getString("gtin")) == null -> "Проверьте штрихкод и контрольную цифру"
                listOf("abv","ibu","volumeMl").any { value.text(it) != null && parseFiniteNumber(value.getString(it)) == null } -> "Проверьте числовые поля"
                (photo != null || value.text("imageAssetId") != null) && (!value.optBoolean("rightsConfirmed") || value.text("imageSource") == null) -> "Укажите источник фото и подтвердите права"
                else -> null
            }
            if (localError == null) {
                for (field in listOf("abv","ibu","volumeMl")) {
                    val raw = value.text(field)?.trim()?.replace(',','.')
                    if (raw == null) value.put(field,JSONObject.NULL)
                    else if (field == "volumeMl") {
                        val volume = raw.toIntOrNull()
                        if (volume == null) localError = "Объём должен быть целым числом миллилитров" else value.put(field,volume)
                    } else value.put(field,parseFiniteNumber(raw)!!)
                }
                for (field in listOf("gtin", "breweryId", "imageAssetId")) if (value.text(field) == null && value.has(field)) value.put(field,JSONObject.NULL)
                if (existing == null) value.put("requestId",requestId)
                if (localError == null) model.submit(value,photo)
            }
        }
    }
}
