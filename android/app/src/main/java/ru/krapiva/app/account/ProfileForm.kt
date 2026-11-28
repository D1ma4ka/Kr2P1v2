package ru.krapiva.app.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.R
import ru.krapiva.app.designsystem.takeCodePoints
import java.net.URI

private enum class SocialNetwork(val key: String, val label: String, @DrawableRes val icon: Int, vararg val hosts: String) {
    TELEGRAM("telegram", "Telegram", R.drawable.ic_profile_telegram, "t.me", "telegram.me"),
    YOUTUBE("youtube", "YouTube", R.drawable.ic_profile_youtube, "youtube.com", "m.youtube.com", "youtu.be"),
    VK("vk", "VK", R.drawable.ic_profile_vk, "vk.com", "m.vk.com", "vk.ru", "m.vk.ru"),
    UNTAPPD("untappd", "Untappd", R.drawable.ic_profile_untappd, "untappd.com");

    fun accepts(value: String): Boolean = runCatching {
        val address = URI(value)
        address.scheme == "https" && address.userInfo == null && address.port in setOf(-1, 443) &&
            address.host?.lowercase()?.removePrefix("www.") in hosts &&
            address.rawPath.orEmpty().split('/').any { it.isNotEmpty() }
    }.getOrDefault(false)
}

private val profileVisibilityOptions = linkedMapOf(
    "private" to "Только я",
    "friends" to "Друзья",
    "public" to "Все пользователи"
)
private val genderOptions = linkedMapOf(
    "" to "Не указывать",
    "Мужской" to "Мужской",
    "Женский" to "Женский"
)
private data class SocialDraft(val url: String, val visibility: String)

@Composable
internal fun ProfileEditor(state: AccountState, model: AccountViewModel) {
    val profile = state.profile ?: return
    val version = profile.getInt("version")
    val oldHandle = profile.optionalText("handle")
    val oldCountry = profile.optionalText("countryId")
    val oldCity = if (profile.isNull("cityId")) null else profile.getInt("cityId")
    val oldGender = profile.optionalText("gender")
    val oldSocial = profile.optJSONObject("socialLinks")
    val enabled = !state.busy && !state.offline

    var handle by rememberSaveable(version) { mutableStateOf(oldHandle.orEmpty()) }
    var firstName by rememberSaveable(version) { mutableStateOf(profile.optionalText("firstName").orEmpty()) }
    var lastName by rememberSaveable(version) { mutableStateOf(profile.optionalText("lastName").orEmpty()) }
    var bio by rememberSaveable(version) { mutableStateOf(profile.optionalText("bio").orEmpty()) }
    var displayName by rememberSaveable(version) { mutableStateOf(profile.getString("displayName")) }
    var gender by rememberSaveable(version) { mutableStateOf(oldGender.takeIf { it in genderOptions }.orEmpty()) }
    var genderChanged by rememberSaveable(version) { mutableStateOf(false) }
    var visibility by rememberSaveable(version) { mutableStateOf(profile.getString("profileVisibility")) }
    var countryId by rememberSaveable(version) { mutableStateOf(oldCountry) }
    var countryName by rememberSaveable(version) { mutableStateOf(profile.optionalText("country").orEmpty()) }
    var cityId by rememberSaveable(version) { mutableStateOf(oldCity) }
    var cityName by rememberSaveable(version) { mutableStateOf(profile.optionalText("city").orEmpty()) }
    var picker by rememberSaveable { mutableStateOf<PlaceKind?>(null) }
    var avatarId by rememberSaveable(version) { mutableStateOf(profile.optionalText("avatarAssetId")) }
    var coverId by rememberSaveable(version) { mutableStateOf(profile.optionalText("coverAssetId")) }
    var avatarUri by rememberSaveable(version) { mutableStateOf<Uri?>(null) }
    var coverUri by rememberSaveable(version) { mutableStateOf<Uri?>(null) }
    var photoNotice by remember { mutableStateOf<String?>(null) }

    val social = remember(version) {
        mutableStateMapOf<SocialNetwork, SocialDraft>().apply {
            SocialNetwork.entries.forEach { network ->
                val existing = oldSocial?.optJSONObject(network.key)
                put(network, SocialDraft(existing?.getString("url").orEmpty(), existing?.getString("visibility") ?: "private"))
            }
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) avatarUri = it
        photoNotice = if (it == null) "Фото не выбрано. Профиль не изменён." else null
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) coverUri = it
        photoNotice = if (it == null) "Обложка не выбрана. Профиль не изменён." else null
    }
    val clearCity = { cityId = null; cityName = "" }

    Title("Редактирование профиля")
    val invalidHandle = oldHandle == null && handle.isNotBlank() && !UserHandle.valid(handle)
    Input("Никнейм", handle, enabled, readOnly = oldHandle != null, keyboard = KeyboardType.Ascii,
        error = if (invalidHandle) "Латинские буквы, цифры и _. От 3 до 24 символов." else null) {
        handle = it.singleLineProfileInput(24)
    }
    Input("Имя", firstName, enabled, capitalization = KeyboardCapitalization.Words) { firstName = it.singleLineProfileInput(80) }
    Input("Фамилия", lastName, enabled, capitalization = KeyboardCapitalization.Words) { lastName = it.singleLineProfileInput(80) }
    ProfileChoice("Пол", gender, genderOptions, enabled) { gender = it; genderChanged = true }
    ProfileLocationField(PlaceKind.COUNTRY, countryName, enabled, onOpen = { picker = PlaceKind.COUNTRY }) {
        countryId = null
        countryName = ""
        clearCity()
    }
    ProfileLocationField(PlaceKind.CITY, cityName, enabled, searchEnabled = enabled && countryId != null,
        onOpen = { picker = PlaceKind.CITY }, onClear = clearCity)
    picker?.let { kind ->
        ProfilePlaceSearch(kind, countryId, model.api, { picker = null }, { choice ->
            if (kind == PlaceKind.COUNTRY) {
                if (countryId != choice.id) clearCity()
                countryId = choice.id
                countryName = choice.name
            } else {
                cityId = choice.id.toInt()
                cityName = choice.name
            }
            picker = null
        }, model::sessionExpired)
    }
    Input("Биография", bio, enabled, singleLine = false, capitalization = KeyboardCapitalization.Sentences) {
        bio = it.replace("\u0000", "").takeCodePoints(2000)
    }

    Text("Социальные сети", style = MaterialTheme.typography.titleLarge)
    SocialNetwork.entries.forEach { network ->
        val draft = social.getValue(network)
        val original = oldSocial?.optJSONObject(network.key)?.getString("url").orEmpty()
        val invalid = draft.url.isNotBlank() && draft.url.trim() != original && !network.accepts(draft.url.trim())
        Input(network.label, draft.url, enabled, keyboard = KeyboardType.Uri,
            leadingIcon = { SocialIcon(network) },
            error = if (invalid) "Введите HTTPS-ссылку на ${network.label}" else null) {
            social[network] = draft.copy(url = it.singleLineProfileInput(2048))
        }
        if (draft.url.isNotBlank()) {
            ProfileChoice("Видимость · ${network.label}", draft.visibility, profileVisibilityOptions, enabled) {
                social[network] = draft.copy(visibility = it)
            }
        }
    }
    Text("Профиль", style = MaterialTheme.typography.titleLarge)
    Input("Отображаемое имя", displayName, enabled, capitalization = KeyboardCapitalization.Words) {
        displayName = it.singleLineProfileInput(80)
    }
    ProfileChoice("Видимость профиля и фото", visibility, profileVisibilityOptions, enabled) { visibility = it }
    photoNotice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Link(if (avatarUri != null) "Другой аватар · фото выбрано" else "Выбрать аватар", enabled) {
        runCatching { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { photoNotice = "Выбор фото недоступен" }
    }
    Link("Удалить аватар", enabled && (avatarUri != null || avatarId != null)) { avatarUri = null; avatarId = null }
    Link(if (coverUri != null) "Другая обложка · фото выбрано" else "Выбрать обложку", enabled) {
        runCatching { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { photoNotice = "Выбор фото недоступен" }
    }
    Link("Удалить обложку", enabled && (coverUri != null || coverId != null)) { coverUri = null; coverId = null }

    val validLinks = social.all { (network, draft) ->
        draft.url.isBlank() || draft.url.trim() == oldSocial?.optJSONObject(network.key)?.getString("url") || network.accepts(draft.url.trim())
    }
    Action("Сохранить", enabled && !invalidHandle && validLinks && displayName.isNotBlank()) {
        val body = JSONObject().put("version", version)
            .put("firstName", firstName.jsonText()).put("lastName", lastName.jsonText())
            .put("country", countryName.jsonText()).put("city", cityName.jsonText())
            .put("bio", bio.jsonText()).put("displayName", displayName.trim())
            .put("gender", (if (genderChanged) gender else oldGender).jsonText())
            .put("genderVisibility", if (genderChanged && gender.isNotBlank()) "public" else profile.getString("genderVisibility"))
            .put("profileVisibility", visibility)
            .put("avatarAssetId", avatarId ?: JSONObject.NULL).put("coverAssetId", coverId ?: JSONObject.NULL)

        if (countryId != oldCountry || cityId != oldCity) {
            body.put("countryId", countryId ?: JSONObject.NULL).put("cityId", cityId ?: JSONObject.NULL)
        }
        if (oldHandle == null && handle.isNotBlank()) body.put("handle", UserHandle.normalize(handle))
        val changes = JSONObject()
        for ((network, draft) in social) {
            val old = oldSocial?.optJSONObject(network.key)
            val url = draft.url.trim()
            if (url.isNotEmpty() && (url != old?.getString("url") || draft.visibility != old?.optString("visibility"))) {
                changes.put(network.key, JSONObject().put("url", url).put("visibility", draft.visibility))
            } else if (url.isEmpty() && old != null) {
                changes.put(network.key, JSONObject.NULL)
            }
        }
        if (changes.length() > 0) body.put("socialLinks", changes)
        model.save(body, avatarUri, coverUri)
    }
    Link("Отменить изменения", enabled) { model.navigate(AccountScreen.PROFILE) }
    if (state.error != null) Link("Загрузить актуальную версию профиля", enabled, model::refresh)
}

private fun String?.jsonText(): Any = this?.takeIf { it.isNotBlank() } ?: JSONObject.NULL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileChoice(
    label: String,
    selected: String,
    options: Map<String, String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = enabled && it }) {
        OutlinedTextField(options[selected].orEmpty(), {}, Modifier.fillMaxWidth().testTag("profile-choice-$label")
            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            label = { Text(label) }, readOnly = true, enabled = enabled, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            for ((value, title) in options) {
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun SocialIcon(network: SocialNetwork) {
    Icon(painterResource(network.icon), contentDescription = null, modifier = Modifier.size(20.dp))
}

@Composable
internal fun ProfileLinks(profile: JSONObject) {
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    fun open(url: String) { failed = runCatching { uriHandler.openUri(url) }.isFailure }
    val used = mutableSetOf<String>()
    val social = profile.optJSONObject("socialLinks")
    for (network in SocialNetwork.entries) {
        val url = social?.optJSONObject(network.key)?.optionalText("url") ?: continue
        used.add(url)
        TextButton({ open(url) }) {
            SocialIcon(network)
            Spacer(Modifier.width(8.dp))
            Text(network.label)
        }
    }
    val legacy = profile.optJSONArray("links") ?: JSONArray()
    for (index in 0 until legacy.length()) {
        val url = legacy.getJSONObject(index).getString("url")
        if (used.add(url)) TextButton({ open(url) }) { Text(url) }
    }
    if (failed) Text("Не удалось открыть ссылку", color = MaterialTheme.colorScheme.error)
}
