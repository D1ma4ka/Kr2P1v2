package ru.krapiva.app.account

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.R
import ru.krapiva.app.designsystem.BeerHero
import ru.krapiva.app.designsystem.KraPivaColors

@Composable
fun AccountApp(model: AccountViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(state.screen) { scroll.scrollTo(0) }
    val authenticated = state.profile != null
    BackHandler(enabled = state.screen !in setOf(AccountScreen.WELCOME, AccountScreen.PROFILE, AccountScreen.RESTORING)) {
        model.navigate(if (authenticated) AccountScreen.PROFILE else AccountScreen.WELCOME)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.screen in setOf(AccountScreen.WELCOME, AccountScreen.REGISTER, AccountScreen.RESTORING)) BeerHero(Modifier.size(88.dp),
                        forceReducedMotion = state.screen == AccountScreen.RESTORING && !state.busy)
                    else Image(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(48.dp))
                    Column { Text("KraPiva", style = MaterialTheme.typography.headlineMedium, color = KraPivaColors.Cream)
                        Text("CRAFT BEER COMMUNITY · 18+", style = MaterialTheme.typography.labelSmall, color = KraPivaColors.Amber) }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                state.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                key(state.screen) {
                    when (state.screen) {
                        AccountScreen.RESTORING -> { Title(if (state.error != null && !state.busy) "Не удалось восстановить сессию" else "Восстанавливаем сессию")
                            if (!state.busy) { Action("Повторить", true, model::restore)
                                Link("Очистить локальную сессию", true, model::clearLocally) } }
                        AccountScreen.WELCOME -> Welcome(state, model)
                        AccountScreen.LOGIN, AccountScreen.REGISTER, AccountScreen.VERIFY,
                        AccountScreen.FORGOT, AccountScreen.RESET -> AuthForm(state, model)
                        AccountScreen.PROFILE -> Profile(state, model)
                        AccountScreen.EDIT -> ProfileEditor(state, model)
                        AccountScreen.SETTINGS -> Settings(state, model)
                    }
                }
            }
            if (authenticated) NavigationBar {
                // Destinations become enabled with their real features in P4–P7.
                for (label in listOf("Главная", "Каталог", "Чекин", "Бары", "Профиль")) NavigationBarItem(
                    selected = label == "Профиль", enabled = label == "Профиль" && !state.busy,
                    onClick = { model.navigate(AccountScreen.PROFILE) }, icon = {
                        if (label == "Чекин") Surface(color = KraPivaColors.Amber, shape = RoundedCornerShape(12.dp)) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { NavigationMark(label, KraPivaColors.Ink) }
                        } else NavigationMark(label, if (label == "Профиль") KraPivaColors.Cream else KraPivaColors.Muted)
                    },
                    label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
    state.fullPhoto?.let { photo -> AlertDialog(onDismissRequest = model::closePhoto,
        title = { Text("Фото профиля") }, text = { Image(photo.asImageBitmap(), "Просмотр фото", Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(model::closePhoto) { Text("Закрыть") } }) }
}
@Composable private fun NavigationMark(label: String, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height; val stroke = Stroke(w * .075f)
        when (label) {
            "Главная" -> drawPath(Path().apply { moveTo(w*.12f,h*.45f); lineTo(w*.5f,h*.12f); lineTo(w*.88f,h*.45f); moveTo(w*.24f,h*.38f); lineTo(w*.24f,h*.88f); lineTo(w*.76f,h*.88f); lineTo(w*.76f,h*.38f) }, color, style = stroke)
            "Каталог" -> {
                drawRect(color, Offset(w*.2f,h*.14f), Size(w*.6f,h*.72f), style = stroke)
                for (y in listOf(.32f,.5f,.68f)) drawLine(color, Offset(w*.33f,h*y), Offset(w*.67f,h*y), w*.06f)
            }
            "Чекин" -> { drawLine(color, Offset(w*.2f,h*.5f), Offset(w*.8f,h*.5f),w*.08f); drawLine(color, Offset(w*.5f,h*.2f), Offset(w*.5f,h*.8f),w*.08f) }
            "Бары" -> {
                drawPath(Path().apply { moveTo(w*.5f,h*.92f); cubicTo(w*.12f,h*.52f,w*.08f,h*.12f,w*.5f,h*.12f); cubicTo(w*.92f,h*.12f,w*.88f,h*.52f,w*.5f,h*.92f) },color,style=stroke)
                drawCircle(color,w*.1f,Offset(w*.5f,h*.37f),style=stroke)
            }
            else -> { drawCircle(color,w*.17f,Offset(w*.5f,h*.28f),style=stroke); drawArc(color,180f,180f,false,Offset(w*.16f,h*.56f),Size(w*.68f,h*.62f),style=stroke) }
        }
    }
}
@Composable private fun Title(value: String) { Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
@Composable private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(label) }
}
@Composable private fun Link(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
@Composable private fun Welcome(state: AccountState, model: AccountViewModel) {
    Title("Открывай пиво.\nСохраняй впечатления.")
    Text("Личный профиль и сообщество любителей крафтового пива. Только для совершеннолетних.", color = KraPivaColors.Muted)
    Action("Создать аккаунт", !state.busy) { model.navigate(AccountScreen.REGISTER) }
    Action("Войти", !state.busy) { model.navigate(AccountScreen.LOGIN) }
    Text("Ответственное потребление. Алкоголь вредит здоровью.", color = KraPivaColors.Muted)
}
@Composable private fun Input(label: String, value: String, enabled: Boolean = true, password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun AuthForm(state: AccountState, model: AccountViewModel) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }; var adult by remember { mutableStateOf(false) }
    val screen = state.screen; val enabled = !state.busy
    Title(when(screen) { AccountScreen.LOGIN -> "Вход"; AccountScreen.REGISTER -> "Новый аккаунт"; AccountScreen.VERIFY -> "Подтверждение почты"; AccountScreen.RESET -> "Новый пароль"; else -> "Восстановление доступа" })
    if (BuildConfig.DEBUG) Text("Локальная тестовая среда: почта @example.invalid; письма в Mailpit.", color = KraPivaColors.Muted)
    if (screen in setOf(AccountScreen.LOGIN, AccountScreen.REGISTER, AccountScreen.FORGOT, AccountScreen.VERIFY)) Input("E-mail", email, enabled, keyboard = KeyboardType.Email) { email = it.take(254) }
    if (screen == AccountScreen.REGISTER) Input("Отображаемое имя", name, enabled) { name = it.take(80) }
    if (screen in setOf(AccountScreen.LOGIN, AccountScreen.REGISTER, AccountScreen.RESET)) Input("Пароль · 12–128 символов", password, enabled, password = true) { password = it.take(128) }
    if (screen in setOf(AccountScreen.REGISTER, AccountScreen.RESET)) Input("Повторите пароль", confirm, enabled, password = true) { confirm = it.take(128) }
    if (screen in setOf(AccountScreen.VERIFY, AccountScreen.RESET)) Input("Одноразовый код из письма", token, enabled) { token = it.trim().take(43) }
    if (screen == AccountScreen.REGISTER) Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(adult, { adult = it }, enabled = enabled); Text("Подтверждаю, что мне исполнилось 18 лет", Modifier.weight(1f))
    }
    when(screen) {
        AccountScreen.LOGIN -> {
            Action("Войти", enabled && email.isNotBlank() && password.isNotEmpty()) { model.login(email, password); password = "" }
            Link("Забыли пароль?", enabled) { model.navigate(AccountScreen.FORGOT) }
            Link("Подтвердить почту", enabled) { model.navigate(AccountScreen.VERIFY) }
        }
        AccountScreen.REGISTER -> Action("Зарегистрироваться", enabled && adult && email.isNotBlank() && name.isNotBlank() && password.length >= 12 && confirm == password) {
            model.register(email, password, name, adult); password = ""; confirm = ""
        }
        AccountScreen.VERIFY -> {
            Action("Подтвердить", enabled && token.length == 43) { model.verify(token); token = "" }
            Link("Отправить письмо снова", enabled && email.isNotBlank()) { model.mail(email, false) }
            Text("Повторная отправка — не чаще раза в минуту. Код действует 30 минут.", color = KraPivaColors.Muted)
        }
        AccountScreen.FORGOT -> Action("Получить код", enabled && email.isNotBlank()) { model.mail(email, true) }
        AccountScreen.RESET -> Action("Сменить пароль", enabled && token.length == 43 && password.length >= 12 && confirm == password) { model.reset(token, password); token = ""; password = ""; confirm = "" }
        else -> Unit
    }
    Link("Назад", enabled) { model.navigate(AccountScreen.WELCOME) }
}
private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
@Composable private fun Profile(state: AccountState, model: AccountViewModel) {
    val p = state.profile ?: return
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    state.cover?.let { Image(it.asImageBitmap(), "Обложка профиля", Modifier.fillMaxWidth().height(144.dp).clickable { model.viewPhoto("coverAssetId") }, contentScale = ContentScale.Crop) }
    state.avatar?.let { Image(it.asImageBitmap(), "Аватар профиля", Modifier.size(96.dp).clickable { model.viewPhoto("avatarAssetId") }, contentScale = ContentScale.Crop) }
    if (state.avatar == null) BeerHero(Modifier.size(72.dp), forceReducedMotion = true)
    Title(p.text("displayName"))
    Text(listOf(p.text("firstName"), p.text("lastName")).filter { it.isNotBlank() }.joinToString(" "))
    Text(listOf(p.text("city"), p.text("country")).filter { it.isNotBlank() }.joinToString(", "), color = KraPivaColors.Muted)
    if (p.text("bio").isNotBlank()) Text(p.text("bio"))
    if (p.text("gender").isNotBlank()) Text("Пол: ${p.text("gender")}")
    val links = p.optJSONArray("links") ?: JSONArray()
    for (i in 0 until links.length()) {
        val url = links.getJSONObject(i).getString("url")
        Link(url, !state.busy) {
            runCatching { uriHandler.openUri(url) }.onFailure { linkError = "Не удалось открыть ссылку. Проверьте наличие браузера." }
        }
    }
    linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("Публичность: ${visibilityLabel(p.text("profileVisibility"))}", color = KraPivaColors.Muted)
    if (state.offline) Text("Сохранённая копия · изменения требуют сети", color = KraPivaColors.Amber)
    Action("Обновить", !state.busy, model::refresh)
    Action("Редактировать профиль", !state.busy && !state.offline) { model.navigate(AccountScreen.EDIT) }
    Link("Настройки аккаунта", !state.busy) { model.navigate(AccountScreen.SETTINGS) }
}
private fun visibilityLabel(value: String) = when(value) { "public" -> "Все пользователи"; "friends" -> "Друзья"; else -> "Только я" }
@Composable private fun Visibility(label: String, value: String, enabled: Boolean, changed: (String) -> Unit) {
    Text("$label: ${visibilityLabel(value)}", color = KraPivaColors.Muted)
    // Wrap rather than squeeze touch targets with large fonts or narrow screens.
    Column { for (choice in listOf("private", "friends", "public")) Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(value == choice, { changed(choice) }, enabled = enabled)
        Text(visibilityLabel(choice))
    } }
}
@Composable private fun ProfileEditor(state: AccountState, model: AccountViewModel) {
    val p = state.profile ?: return
    val fields = remember { mutableStateMapOf<String, String>().apply { for (key in listOf("displayName", "firstName", "lastName", "city", "country", "bio", "gender")) put(key, p.text(key)) } }
    var visibility by remember { mutableStateOf(p.text("profileVisibility")) }
    var genderVisibility by remember { mutableStateOf(p.text("genderVisibility")) }
    var avatarId by remember { mutableStateOf(p.text("avatarAssetId")) }; var coverId by remember { mutableStateOf(p.text("coverAssetId")) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }; var coverUri by remember { mutableStateOf<Uri?>(null) }
    val links = remember { mutableStateListOf<Pair<String, String>>().apply {
        val original = p.optJSONArray("links") ?: JSONArray()
        for (i in 0 until original.length()) add(original.getJSONObject(i).getString("url") to original.getJSONObject(i).getString("visibility"))
    } }
    var pickerNotice by remember { mutableStateOf<String?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) { avatarUri = it; pickerNotice = null } else pickerNotice = "Фото не выбрано. Профиль не изменён." }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) { coverUri = it; pickerNotice = null } else pickerNotice = "Обложка не выбрана. Профиль не изменён." }
    val enabled = !state.busy
    Title("Редактирование профиля")
    pickerNotice?.let { Text(it, color = KraPivaColors.Muted) }
    Text("Фотографии загружаются при сохранении. JPEG, PNG или WebP до 8 МБ.", color = KraPivaColors.Muted)
    Link(if (avatarUri != null) "Другой аватар · фото выбрано" else "Выбрать аватар", enabled) { runCatching { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { pickerNotice = "Выбор фото недоступен. Попробуйте снова." } }
    Link("Удалить аватар", enabled && (avatarUri != null || avatarId.isNotEmpty())) { avatarUri = null; avatarId = "" }
    Link(if (coverUri != null) "Другая обложка · фото выбрано" else "Выбрать обложку", enabled) { runCatching { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { pickerNotice = "Выбор фото недоступен. Попробуйте снова." } }
    Link("Удалить обложку", enabled && (coverUri != null || coverId.isNotEmpty())) { coverUri = null; coverId = "" }
    for ((key, label, max) in listOf(Triple("displayName", "Отображаемое имя", 80), Triple("firstName", "Имя", 80), Triple("lastName", "Фамилия", 80),
        Triple("country", "Страна", 100), Triple("city", "Город", 120), Triple("bio", "О себе", 2000), Triple("gender", "Пол · необязательно", 80))) {
        Input(label, fields[key].orEmpty(), enabled, singleLine = key != "bio") { fields[key] = it.take(max) }
    }
    Visibility("Профиль и фотографии", visibility, enabled) { visibility = it }
    Visibility("Пол", genderVisibility, enabled) { genderVisibility = it }
    for (i in links.indices) {
        Input("Ссылка ${i + 1} · https://", links[i].first, enabled, keyboard = KeyboardType.Uri) { links[i] = it.take(2048) to links[i].second }
        Visibility("Видимость ссылки ${i + 1}", links[i].second, enabled) { links[i] = links[i].first to it }
        Link("Убрать ссылку ${i + 1}", enabled) { links.removeAt(i) }
    }
    Link("Добавить ссылку", enabled && links.size < 10) { links.add("" to "private") }
    Action("Сохранить", enabled && fields["displayName"].orEmpty().isNotBlank()) {
        val body = JSONObject().put("version", p.getInt("version")).put("profileVisibility", visibility).put("genderVisibility", genderVisibility)
            .put("avatarAssetId", avatarId.ifEmpty { null } ?: JSONObject.NULL).put("coverAssetId", coverId.ifEmpty { null } ?: JSONObject.NULL)
        for ((key, value) in fields) body.put(key, if (key == "displayName") value.trim() else value.ifBlank { null } ?: JSONObject.NULL)
        body.put("links", JSONArray().apply { for (link in links) put(JSONObject().put("url", link.first.trim()).put("visibility", link.second)) })
        model.save(body, avatarUri, coverUri)
    }
    Link("Отменить изменения", enabled) { model.navigate(AccountScreen.PROFILE) }
    if (state.error != null) Link("Загрузить актуальную версию профиля", enabled, model::refresh)
}
@Composable private fun Settings(state: AccountState, model: AccountViewModel) {
    var deleteDialog by remember { mutableStateOf(false) }
    Title("Аккаунт и приватность")
    Text("Публичность полей и фото проверяется на сервере. Настройте её при редактировании профиля.")
    Link("Редактировать приватность", !state.busy && !state.offline) { model.navigate(AccountScreen.EDIT) }
    Action("Выйти из аккаунта", !state.busy, model::logout)
    if (state.error != null || state.offline) Link("Очистить данные на этом телефоне", !state.busy, model::clearLocally)
    Link("Запросить удаление аккаунта", !state.busy) { deleteDialog = true }
    Link("К профилю", !state.busy) { model.navigate(AccountScreen.PROFILE) }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Удалить аккаунт?") },
        text = { Text("Доступ и публичность отключатся сразу. Запрос на удаление личных материалов будет сохранён на сервере со сроком до 30 дней. Обязательные по закону записи могут сохраняться при законном основании.") },
        confirmButton = { TextButton({ deleteDialog = false; model.deleteAccount() }) { Text("Отправить запрос") } },
        dismissButton = { TextButton({ deleteDialog = false }) { Text("Отмена") } })
}
