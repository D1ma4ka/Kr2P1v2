package ru.krapiva.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.DiaryDates
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.diary.objects
import ru.krapiva.app.diary.strings
import ru.krapiva.app.places.*

internal data class HomeDiscovery(
    val data: JSONObject? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
internal fun rememberHomeDiscovery(api: AccountApi, city: DiscoveryCity?, radius: Int, refresh: Int): State<HomeDiscovery> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    return produceState(HomeDiscovery(), api, city, radius, refresh, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = HomeDiscovery()
            try {
                val center = if (city != null) city.center else if (hasLocationPermission(context)) {
                    value = HomeDiscovery(loading = true)
                    withTimeoutOrNull(8_000) { currentLocation(context) }?.let { Coordinates(it.latitude, it.longitude) }
                } else null
                if (center != null) {
                    value = HomeDiscovery(loading = true)
                    val data = api.json("v1/discover/nearby", "POST", center.request(radius))
                    value = HomeDiscovery(data = data)
                } else {
                    value = HomeDiscovery()
                }
                awaitCancellation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                value = HomeDiscovery(error = "Не удалось загрузить места и события.")
                awaitCancellation()
            } finally {
                value = HomeDiscovery()
            }
        }
    }
}

@Composable
internal fun DiscoveryContent(
    state: HomeDiscovery,
    city: DiscoveryCity?,
    radius: Int,
    onMap: () -> Unit,
    onVenue: (String) -> Unit,
    onBadges: () -> Unit,
) {
    var details by remember { mutableStateOf<JSONObject?>(null) }
    var showList by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Места рядом", style = MaterialTheme.typography.headlineSmall)
        if (state.data != null || city != null) {
            Text(listOfNotNull(city?.name, "$radius км".takeIf { state.data != null }).joinToString(" · "), color = KraPivaColors.Muted)
        }
        val data = state.data
        when {
            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
            data == null -> Text("Открой карту, чтобы выбрать город или использовать геопозицию.", color = KraPivaColors.Muted)
            else -> {
                val places = data.getJSONArray("places").objects()
                if (places.isEmpty()) Text("В этом радиусе пока нет подтверждённых мест.", color = KraPivaColors.Muted)
                for (place in places) {
                    ClubCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { onVenue(place.getString("id")) }) {
                        Text(place.getString("name"), style = MaterialTheme.typography.titleMedium)
                        Text("${venueTypeLabel(place.getString("type"))} · ${DiaryNumbers.average(place.text("distanceKm"))} км")
                        place.text("address")?.let { Text(it, color = KraPivaColors.Muted) }
                        if (place.optBoolean("testOnly")) Text("Тестовая запись", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Button(onMap, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Открыть карту") }
        if (data != null) {
            Text("События рядом", style = MaterialTheme.typography.titleLarge)
            val events = data.getJSONArray("events").objects()
            if (events.isEmpty()) Text("В этом радиусе пока нет опубликованных событий.", color = KraPivaColors.Muted)
            for (event in events.take(3)) {
                ClubCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { details = event }) {
                    Text(event.getString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(event.getString("venueName"), color = KraPivaColors.Muted)
                    Text(DiaryDates.display(event.getString("startsAt")))
                }
            }
            if (events.size > 3) {
                OutlinedButton({ showList = "events" }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Все события рядом")
                }
            }
            Text("Локальные значки", style = MaterialTheme.typography.titleLarge)
            val badges = data.getJSONArray("badges").objects()
            if (badges.isEmpty()) Text("В этом радиусе пока нет локальных значков.", color = KraPivaColors.Muted)
            for (badge in badges.take(3)) {
                ClubCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { details = badge }) {
                    Text(badge.getString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(badge.text("eventTitle") ?: badge.getString("venueName"), color = KraPivaColors.Muted)
                }
            }
            if (badges.size > 3) {
                OutlinedButton({ showList = "badges" }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Все локальные значки")
                }
            }
        }
        OutlinedButton(onBadges, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Все достижения") }
    }
    showList?.let { key ->
        AlertDialog(
            onDismissRequest = { showList = null },
            title = { Text(if (key == "events") "События рядом" else "Локальные значки") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(items = state.data?.optJSONArray(key)?.objects().orEmpty(), key = { it.getString("id") }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.getString("title")) },
                            supportingContent = { Text(entry.getString("venueName")) },
                            modifier = Modifier.clickable(role = Role.Button) { showList = null; details = entry },
                        )
                    }
                }
            },
            confirmButton = { TextButton({ showList = null }) { Text("Закрыть") } },
        )
    }
    details?.let { entry ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(entry.getString("title")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(listOfNotNull(entry.text("venueName"), entry.text("address")).joinToString(" · "))
                    Text("Начало · ${DiaryDates.display(entry.getString("startsAt"))}")
                    Text("Окончание · ${DiaryDates.display(entry.getString("endsAt"))}")
                    Text(entry.getString("description"))
                    entry.optJSONArray("badgeTitles")?.strings()?.takeIf { it.isNotEmpty() }?.let {
                        Text("Доступные значки · ${it.joinToString(", ")}")
                    }
                    if (entry.has("eventId")) Text("Для получения значка модератор подтверждает посещение.")
                }
            },
            confirmButton = {
                TextButton({ details = null; onVenue(entry.getString("venueId")) }) { Text("Открыть место") }
            },
            dismissButton = { TextButton({ details = null }) { Text("Закрыть") } },
        )
    }
}
