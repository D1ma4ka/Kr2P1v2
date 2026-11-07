package ru.krapiva.app.social

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.*

@Composable
fun SocialApp(api: AccountApi,userId: String,onHome: () -> Unit,onProfile: () -> Unit,onPosition: (String) -> Unit,expired: () -> Unit,initialCheckin: String? = null,providedModel: SocialViewModel? = null) {
    val model = providedModel ?: viewModel<SocialViewModel>(key = "social-$userId",factory = object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = SocialViewModel(api,expired) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(initialCheckin) { if(initialCheckin != null) model.open(SocialPage.DETAIL,initialCheckin) else model.refresh() }
    DisposableEffect(owner,model) {
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_STOP) model.hide() else if(event == Lifecycle.Event.ON_START) model.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);model.hide() }
    }
    BackHandler { if(state.page == SocialPage.FEED) onHome() else model.open(SocialPage.FEED) }
    var remove by remember { mutableStateOf<Pair<String,Boolean>?>(null) }
    Surface(Modifier.fillMaxSize(),color = KraPivaColors.Ink) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onHome,enabled = !state.busy) { Text("Главная") }
                TextButton(onProfile,enabled = !state.busy) { Text("Мой профиль") }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceEvenly) {
                for((page,label) in listOf(SocialPage.FEED to "Лента",SocialPage.SEARCH to "Поиск",SocialPage.FRIENDS to "Друзья",SocialPage.REQUESTS to "Заявки")) TextButton({ model.open(page) },enabled = !state.busy) { Text(label,color = if(state.page == page)KraPivaColors.Amber else KraPivaColors.Cream) }
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { DiaryTitle(when(state.page) { SocialPage.FEED -> "Лента друзей";SocialPage.SEARCH -> "Найти друзей";SocialPage.FRIENDS -> "Мои друзья";SocialPage.REQUESTS -> "Заявки в друзья";SocialPage.PROFILE -> "Профиль пользователя";SocialPage.DETAIL -> "Обсуждение чекина" }) }
                item { TextButton({ model.refresh() },enabled = !state.busy) { Text("Обновить") } }
                state.error?.let { error -> item { Text(error,color = MaterialTheme.colorScheme.error,modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
                if(state.page == SocialPage.SEARCH) item {
                    OutlinedTextField(state.query,model::query,label = { Text("Имя или никнейм") },singleLine = true,enabled = !state.busy,modifier = Modifier.fillMaxWidth())
                    DiaryButton("Найти",!state.busy && state.query.trim().length >= 2) { model.refresh() }
                    Text("Поиск учитывает публичность профиля. Для новых знакомств откройте свой профиль всем пользователям в настройках.",color = KraPivaColors.Muted)
                }
                if(state.page == SocialPage.PROFILE) state.data?.let { p -> item {
                    key(state.revision) {
                        p.text("coverAssetId")?.let { SocialPhoto(api,it,"Обложка пользователя",true) }
                        p.text("avatarAssetId")?.let { SocialPhoto(api,it,"Аватар пользователя") }
                    }
                    Text(p.getString("displayName"),style = MaterialTheme.typography.headlineSmall)
                    Text(listOfNotNull(p.text("firstName"),p.text("lastName"),p.text("city"),p.text("country")).joinToString(" · "))
                    p.text("bio")?.let { Text(it) };p.text("gender")?.let { Text(it) }
                    val handler = LocalUriHandler.current
                    var linkError by remember { mutableStateOf(false) }
                    for(link in p.getJSONArray("links").objects()) TextButton({ linkError = runCatching { handler.openUri(link.getString("url")) }.isFailure }) { Text(link.getString("url")) }
                    if(linkError) Text("Не удалось открыть ссылку",color = MaterialTheme.colorScheme.error)
                    state.statistics?.let { stats -> DiaryCard {
                        Text("Доступные чекины: ${stats.getInt("totalCheckins")}")
                        Text("Уникальных позиций: ${stats.getInt("uniquePositions")}")
                        Text("Средняя оценка: ${DiaryNumbers.average(stats.text("averageRating"))}")
                        Text("Только записи, доступные вам",color = KraPivaColors.Muted)
                    } }
                    if(p.getString("userId") != userId) state.relationship?.let { rel -> when {
                        rel.getBoolean("friends") -> TextButton({ remove = p.getString("userId") to true },enabled = !state.busy) { Text("Удалить из друзей") }
                        rel.text("incomingId") != null -> { DiaryButton("Принять заявку",!state.busy) { model.decide(rel.getString("incomingId"),"accepted") };TextButton({ model.decide(rel.getString("incomingId"),"rejected") },enabled = !state.busy) { Text("Отклонить заявку") } }
                        rel.text("outgoingId") != null -> TextButton({ model.decide(rel.getString("outgoingId"),"cancelled") },enabled = !state.busy) { Text("Отменить заявку") }
                        else -> DiaryButton("Добавить в друзья",!state.busy,model::invite)
                    } }
                } }
                if(state.page == SocialPage.DETAIL) state.data?.let { row -> item {
                    SocialCheckin(row,api,state.revision,!state.busy,{ model.open(SocialPage.PROFILE,row.getString("userId")) },null)
                    TextButton({ onPosition(row.getString("positionId")) },enabled = !state.busy) { Text("Открыть позицию") }
                    DiaryButton(if(row.getBoolean("liked"))"Убрать лайк · ${row.getInt("likeCount")}" else "Нравится · ${row.getInt("likeCount")}",!state.busy,model::like)
                    Text("Комментарии: ${row.getInt("commentCount")}",style = MaterialTheme.typography.titleLarge)
                } }
                // Keep an uncertain comment's UUID and exact text for a safe retry.
                if(state.page == SocialPage.DETAIL) item {
                    OutlinedTextField(state.comment,model::comment,label = { Text("Ваш комментарий") },enabled = !state.busy && !state.pendingComment,modifier = Modifier.fillMaxWidth(),maxLines = 6)
                    DiaryButton(if(state.pendingComment)"Повторить отправку" else "Отправить комментарий",!state.busy && state.comment.isNotBlank(),model::sendComment)
                }
                if(state.items.isEmpty() && !state.busy && state.error == null) item { Text(when(state.page) { SocialPage.FEED -> "В ленте пока пусто. Найдите друзей или обновите её позже.";SocialPage.REQUESTS -> "Нет ожидающих заявок";SocialPage.FRIENDS -> "Друзей пока нет";SocialPage.DETAIL -> "Комментариев пока нет";SocialPage.SEARCH -> "Введите имя — от двух символов. Если результатов нет, проверьте запрос.";else -> "Нет доступных записей" },color = KraPivaColors.Muted) }
                items(state.items,key = { it.optString("id",it.optString("userId")) }) { item ->
                    when(state.page) {
                        SocialPage.FEED,SocialPage.PROFILE -> SocialCheckin(item,api,state.revision,!state.busy,{ model.open(SocialPage.PROFILE,item.getString("userId")) },{ model.open(SocialPage.DETAIL,item.getString("id")) })
                        SocialPage.DETAIL -> DiaryCard {
                            Text(item.getString("displayName"),style = MaterialTheme.typography.titleMedium)
                            Text(item.getString("body"));Text(DiaryDates.display(item.getString("createdAt")),color = KraPivaColors.Muted)
                            if(item.getString("userId") == userId) TextButton({ remove = item.getString("id") to false },enabled = !state.busy) { Text("Удалить комментарий") }
                        }
                        else -> DiaryCard {
                            Text(item.getString("displayName"),style = MaterialTheme.typography.titleLarge)
                            TextButton({ model.open(SocialPage.PROFILE,item.getString("userId")) },enabled = !state.busy) { Text("Открыть профиль") }
                            if(state.page == SocialPage.FRIENDS) TextButton({ remove = item.getString("userId") to true },enabled = !state.busy) { Text("Удалить из друзей") }
                            if(state.page == SocialPage.REQUESTS) {
                                if(item.getString("toUserId") == userId) { DiaryButton("Принять",!state.busy) { model.decide(item.getString("id"),"accepted") };TextButton({ model.decide(item.getString("id"),"rejected") },enabled = !state.busy) { Text("Отклонить") } }
                                else { Text("Ожидает ответа",color = KraPivaColors.Muted);TextButton({ model.decide(item.getString("id"),"cancelled") },enabled = !state.busy) { Text("Отменить заявку") } }
                            }
                        }
                    }
                }
                if(state.next != null) item { DiaryButton("Следующая страница",!state.busy) { model.refresh(true) } }
            }
        }
    }
    remove?.let { (id,friend) -> AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { remove = null },title = { Text(if(friend)"Удалить из друзей?" else "Удалить комментарий?") },text = { Text(if(friend)"Доступ к записям для друзей прекратится. Для новой дружбы потребуется заявка." else "Комментарий исчезнет из обсуждения.") },confirmButton = { TextButton({ remove = null;if(friend)model.removeFriend(id)else model.removeComment(id) }) { Text("Удалить") } },dismissButton = { TextButton({ remove = null }) { Text("Отмена") } }) }
}
@Composable private fun SocialCheckin(row: JSONObject,api: AccountApi,revision: Int,enabled: Boolean,onUser: () -> Unit,onDetail: (() -> Unit)?) {
    DiaryCard {
        TextButton(onUser,enabled = enabled) { Text(row.getString("displayName")) }
        CheckinCard(row,enabled && onDetail != null) { onDetail?.invoke() }
        key(row.getString("id"),revision) { for(id in row.getJSONArray("mediaIds").strings()) SocialPhoto(api,id,"Фото в ленте") }
        if(onDetail != null) TextButton(onDetail,enabled = enabled) { Text("Лайки: ${row.getInt("likeCount")} · комментарии: ${row.getInt("commentCount")}") }
    }
}
@Composable private fun SocialPhoto(api: AccountApi,id: String,label: String,cover: Boolean = false) {
    var full by remember(id) { mutableStateOf(false) }
    var loading by remember(id) { mutableStateOf(true) }
    val result by produceState<ByteArray?>(null,id,full) {
        value = null;loading = true
        try { value = api.bytes("v1/media/$id" + if(full)"" else "?size=thumbnail") }
        catch(e: Exception) { if(e is CancellationException) throw e }
        finally { loading = false }
    }
    val bitmap = remember(result) { result?.let { BitmapFactory.decodeByteArray(it,0,it.size) } }
    if(bitmap == null) Text(if(loading)"Загрузка фото…" else "Фото недоступно. Обновите страницу.",color = KraPivaColors.Muted)
    else Image(bitmap.asImageBitmap(),label,Modifier.fillMaxWidth().height(if(cover)140.dp else 180.dp).clickable { full = true },contentScale = ContentScale.Fit)
    if(full) AlertDialog(containerColor = KraPivaColors.Surface,onDismissRequest = { full = false },text = { bitmap?.let { Image(it.asImageBitmap(),"Просмотр фото",Modifier.fillMaxWidth().heightIn(max = 420.dp),contentScale = ContentScale.Fit) } },confirmButton = { TextButton({ full = false }) { Text("Закрыть") } })
}
