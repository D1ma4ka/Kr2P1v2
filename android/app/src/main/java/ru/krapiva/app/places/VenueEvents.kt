package ru.krapiva.app.places

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.designsystem.AmberAction
import ru.krapiva.app.diary.DiaryDates
import ru.krapiva.app.diary.objects
import ru.krapiva.app.home.DiscoveryDetails

@Composable
internal fun VenueEvents(api: AccountApi, venueId: String, expired: () -> Unit) {
    var open by rememberSaveable(venueId) { mutableStateOf(false) }
    AmberAction("События заведения", { open = true })
    if (!open) return
    var data by remember(venueId) { mutableStateOf<JSONObject?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(api, venueId, attempt) {
        failed = false
        try {
            data = api.json("v1/venues/$venueId/events")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiFailure && e.status == 401) expired()
            failed = true
        }
    }
    val event = selected
    if (event != null) {
        DiscoveryDetails(event, { selected = null })
        return
    }
    AlertDialog(onDismissRequest = { open = false }, title = { Text("События заведения") }, text = {
        Column {
            val rows = data?.getJSONArray("items")?.objects()
            when {
                failed -> { Text("Не удалось загрузить события."); TextButton({ attempt++ }) { Text("Повторить") } }
                rows == null -> LinearProgressIndicator()
                rows.isEmpty() -> Text("У этого места пока нет опубликованных событий.")
                else -> {
                    if (data?.optBoolean("truncated") == true) Text("Показаны первые 30 событий.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(rows, key = { it.getString("id") }) { row ->
                            TextButton({ selected = row }) {
                                Text("${row.getString("title")} · ${DiaryDates.display(row.getString("startsAt"))}")
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton({ open = false }) { Text("Закрыть") } })
}
