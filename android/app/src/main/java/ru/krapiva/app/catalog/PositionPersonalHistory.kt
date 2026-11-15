package ru.krapiva.app.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.*

/** Own history uses the existing owner-scoped endpoint; never infers package relationships. */
@Composable internal fun PositionPersonalHistory(api: AccountApi, id: String, openCheckin: (String) -> Unit) {
    var rows by remember(id) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var expanded by remember(id) { mutableStateOf(false) }
    var next by remember(id) { mutableStateOf<String?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var busy by remember(id) { mutableStateOf(false) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean = false) {
        if(busy) return
        busy = true
        scope.launch {
            try {
                val path = "v1/checkins?positionId=${android.net.Uri.encode(id)}&limit=20" +
                    (if(more) next?.let { "&cursor=${android.net.Uri.encode(it)}" }.orEmpty() else "")
                val (data,offline) = api.catalogGet(path)
                rows = ((if(more)rows else emptyList()) + data.getJSONArray("items").objects()).distinctBy { it.getString("id") }
                next = data.text("nextCursor"); loaded = true
                message = if(offline) "Сохранённая история · статус мог измениться" else null
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                message = "История недоступна. Повторите запрос при появлении связи."
            } finally { busy = false }
        }
    }
    ClubCard {
        Text("Мои записи об этой позиции",style = MaterialTheme.typography.titleLarge)
        TextButton({ expanded = !expanded;if(expanded && !loaded) load() },enabled = !busy) { Text(if(expanded) "Скрыть историю" else "Открыть мою историю") }
        if(expanded) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it,color = KraPivaColors.Muted) }
            if(loaded && rows.isEmpty()) Text("У вас пока нет чекинов этой позиции.")
            rows.forEach { row ->
                TextButton({ openCheckin(row.getString("id")) }) {
                    Text("${DiaryDates.display(row.getString("occurredAt"))} · ${if(row.isNull("ratingQuarters")) "Без оценки" else DiaryNumbers.rating(row.getInt("ratingQuarters"))}")
                }
            }
            if(next != null) TextButton({ load(true) },enabled = !busy) { Text("Ещё мои записи") }
            TextButton({ load() },enabled = !busy) { Text("Обновить историю") }
        }
    }
}
