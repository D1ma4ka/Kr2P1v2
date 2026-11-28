package ru.krapiva.app.account

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import ru.krapiva.app.designsystem.ClubGlyph
import ru.krapiva.app.designsystem.ClubIcon
import ru.krapiva.app.designsystem.takeCodePoints

internal enum class PlaceKind(val label: String, val searchLabel: String, val endpoint: String) {
    COUNTRY("Страна", "Поиск страны", "countries"),
    CITY("Город", "Поиск города", "cities")
}

internal data class ProfilePlace(val id: String, val name: String, val regionCode: String)

@Composable
internal fun ProfileLocationField(
    kind: PlaceKind,
    name: String,
    enabled: Boolean,
    searchEnabled: Boolean = enabled,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    val label = if (kind == PlaceKind.COUNTRY) "Выбрать страну" else "Выбрать город"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = 54.dp).testTag("profile-${kind.name.lowercase()}"),
            enabled = searchEnabled, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                if (name.isNotBlank()) Text(kind.label, style = MaterialTheme.typography.labelMedium)
                Text(name.ifBlank { label })
            }
            ClubGlyph(ClubIcon.CHEVRON, color = LocalContentColor.current)
        }
        if (name.isNotBlank()) TextButton(onClear, enabled = enabled, modifier = Modifier.heightIn(min = 54.dp)) {
            Text("Убрать", modifier = Modifier.testTag("clear-${kind.name.lowercase()}"))
        }
    }
}

@Composable
internal fun ProfilePlaceSearch(
    kind: PlaceKind,
    countryId: String?,
    api: AccountApi,
    onClose: () -> Unit,
    onSelect: (ProfilePlace) -> Unit,
    onSessionExpired: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(emptyList<ProfilePlace>()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(kind, countryId, query, retry) {
        results = emptyList()
        loading = true
        failed = false
        if (query.isNotBlank()) delay(180)
        val scope = if (kind == PlaceKind.CITY) "&countryId=${Uri.encode(countryId.orEmpty())}" else ""
        try {
            val items = api.json("v1/me/profile/locations/${kind.endpoint}?q=${Uri.encode(query.trim())}$scope").getJSONArray("items")
            results = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                ProfilePlace(item.getString("id"), item.getString("name"), item.optionalText("regionCode").orEmpty())
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is ApiFailure && error.status == 401) {
                onClose()
                onSessionExpired()
            } else failed = true
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind.label, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(query, { query = it.singleLineProfileInput(120) }, Modifier.fillMaxWidth(),
                    label = { Text(kind.searchLabel) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search))
                when {
                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    failed -> {
                        Text("Не удалось загрузить варианты", color = MaterialTheme.colorScheme.error)
                        TextButton({ retry++ }) { Text("Повторить") }
                    }
                    results.isEmpty() -> Text("Ничего не найдено")
                    else -> {
                        val duplicateNames = results.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(results, key = { it.id }) { place ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (place.name in duplicateNames && place.regionCode.isNotBlank()) {
                                            "${place.name} · ${place.regionCode}"
                                        } else place.name)
                                    },
                                    onClick = { onSelect(place) },
                                    modifier = Modifier.testTag("place-${place.id}")
                                )
                            }
                        }
                    }
                }
                TextButton(onClose, Modifier.align(androidx.compose.ui.Alignment.End)) { Text("Отмена") }
            }
        }
    }
}

internal fun JSONObject.optionalText(key: String): String? =
    optString(key).takeUnless { isNull(key) || it.isBlank() }

internal fun String.singleLineProfileInput(limit: Int): String =
    replace("\r", "").replace("\n", "").replace("\u0000", "").takeCodePoints(limit)
