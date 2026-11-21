package ru.krapiva.app.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.DiaryDates
import ru.krapiva.app.diary.DiaryNumbers

@Composable
fun HomeApp(api: AccountApi, profile: JSONObject, avatar: Bitmap?, onCheckin: () -> Unit,
    onCollection: () -> Unit, onOwnCheckin: (String) -> Unit, onSocial: (String?) -> Unit,
    onFriends: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit, expired: () -> Unit,
    bottomBar: @Composable () -> Unit, providedModel: HomeViewModel? = null,onPlaces: ()->Unit={},onBadges: ()->Unit={}) {
    val model = providedModel ?: viewModel<HomeViewModel>(key = "home-${profile.getString("userId")}",factory = object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = HomeViewModel(api,expired) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner,model) {
        val observer = LifecycleEventObserver { _,event ->
            if(event == Lifecycle.Event.ON_START) model.refresh()
            if(event == Lifecycle.Event.ON_STOP) model.hide()
        }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.refresh()
        onDispose { owner.lifecycle.removeObserver(observer);model.hide() }
    }
    KraPivaBackdrop {
        Column(Modifier.safeDrawingPadding()) {
            HomeContent(state,profile,avatar,api,onCheckin,onCollection,onOwnCheckin,onSocial,onFriends,onProfile,onSettings,model::refresh,model::like,Modifier.weight(1f),onPlaces,onBadges)
            bottomBar()
        }
    }
}

@Composable
private fun HomeContent(state: HomeState, profile: JSONObject, avatar: Bitmap?, api: AccountApi,
    onCheckin: () -> Unit,onCollection: () -> Unit,onOwnCheckin: (String) -> Unit,onSocial: (String?) -> Unit,
    onFriends: () -> Unit,onProfile: () -> Unit,onSettings: () -> Unit,onRefresh: () -> Unit,onLike: () -> Unit,modifier: Modifier,onPlaces: ()->Unit,onBadges: ()->Unit) {
    val scroll = rememberLazyListState()
    LazyColumn(modifier.fillMaxWidth().testTag("club-home"),state = scroll,contentPadding = PaddingValues(horizontal = 16.dp,vertical = 12.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceBetween) {
                ClubBrand()
                // Settings is a real destination; no inert notification bell for future P7.
                IconButton(onSettings,Modifier.testTag("home-settings")) { ClubGlyph(ClubIcon.SETTINGS,description = "Настройки") }
            }
        }
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onPlaces){Text("Места и карта")};TextButton(onBadges){Text("Достижения")}
        }}
        item {
            BoxWithConstraints {
                val inline = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.15f
                Column {
                    Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(profile.getString("displayName"),avatar,Modifier.size(48.dp).clickable(onClick = onProfile))
                        Column(Modifier.weight(1f)) {
                            val name = profile.text("firstName") ?: profile.getString("displayName")
                            Text("Привет, $name!",style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
                            Text("Твоя пивная история",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
                        }
                        if(inline) AmberAction("Чекин",onCheckin,Modifier.testTag("home-new-checkin"))
                    }
                    if(!inline) Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) { AmberAction("Чекин",onCheckin,Modifier.testTag("home-new-checkin")) }
                }
            }
        }
        item {
            ClubCard {
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Metric(state.summary?.optInt("totalCheckins")?.toString() ?: "—","чекинов",ClubIcon.GLASS,Modifier.weight(1f),onCollection)
                    VerticalDivider(Modifier.height(36.dp),color = KraPivaColors.Border)
                    Metric(state.summary?.optInt("uniquePositions")?.toString() ?: "—","позиций",ClubIcon.BOTTLE,Modifier.weight(1f),onCollection)
                    VerticalDivider(Modifier.height(36.dp),color = KraPivaColors.Border)
                    Metric(state.friends ?: "—","друзей",ClubIcon.PEOPLE,Modifier.weight(1f),onFriends)
                }
            }
        }
        if(state.offline) item { Text("Нет связи · сохранённая копия ваших записей",color = KraPivaColors.Muted,style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { message -> item { StatusCard("Записи недоступны",message,"Повторить",onRefresh) } }
        item { SectionHeading("Последняя запись") }
        item {
            val latest = state.latest
            when {
                state.loading && latest == null -> LoadingCard()
                latest != null -> LatestCard(latest,api,state.offline) { onOwnCheckin(latest.getString("id")) }
                state.error == null -> StatusCard("Первый чекин","Сохрани первое впечатление о пиве, чтобы увидеть здесь свою запись.","Создать чекин",onCheckin)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeading("Друзья в ленте",Modifier.weight(1f))
                TextButton({ onSocial(null) }) { Text("Все →") }
            }
        }
        item {
            when {
                state.loading && state.feed == null -> LoadingCard()
                state.socialError != null -> StatusCard("Лента недоступна",state.socialError,"Повторить",onRefresh)
                state.feed != null -> key(state.revision) { FriendCard(state,api,onLike) { onSocial(state.feed.getString("id")) } }
                else -> StatusCard("Здесь будут ваши друзья","Найди друзей, чтобы видеть их новые записи и обсуждать впечатления.","Найти друзей",onFriends)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onCollection) { Text("Мои записи") }
                TextButton(onRefresh,enabled = !state.loading) { ClubGlyph(ClubIcon.REFRESH,Modifier.size(16.dp),KraPivaColors.Amber);Spacer(Modifier.width(6.dp));Text("Обновить") }
            }
        }
    }
}
@Composable private fun SectionHeading(text: String,modifier: Modifier = Modifier) {
    Text(text,modifier.semantics { heading() },style = MaterialTheme.typography.headlineSmall)
}
@Composable private fun RowScope.Metric(value: String,label: String,icon: ClubIcon,modifier: Modifier,onClick: () -> Unit) {
    Column(modifier.heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClubGlyph(icon,Modifier.size(24.dp));Text(value,style = MaterialTheme.typography.headlineMedium)
        }
        Text(label,style = MaterialTheme.typography.bodyMedium,color = KraPivaColors.Muted)
    }
}
@Composable private fun LatestCard(row: JSONObject,api: AccountApi,offline: Boolean,onClick: () -> Unit) {
    ClubCard(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("home-latest")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClubPhoto(api,row.optJSONArray("mediaIds")?.optString(0)?.takeIf { it.isNotBlank() },row.getString("positionName"),Modifier.width(112.dp).height(150.dp),cacheOwn = true,offline = offline)
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.getString("positionName"),style = MaterialTheme.typography.titleMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                Text(row.getString("breweryName"),style = MaterialTheme.typography.bodyMedium,color = KraPivaColors.Muted,maxLines = 1,overflow = TextOverflow.Ellipsis)
                row.text("styleName")?.let { Text(it,color = KraPivaColors.Muted,style = MaterialTheme.typography.bodySmall) }
                Rating(row)
                row.text("body")?.takeIf { it.isNotBlank() }?.let { Text(it,style = MaterialTheme.typography.bodyMedium,fontStyle = FontStyle.Italic,maxLines = 2,overflow = TextOverflow.Ellipsis) }
                Text(DiaryDates.display(row.getString("occurredAt")),style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
            }
        }
    }
}
@Composable private fun FriendCard(state: HomeState,api: AccountApi,onLike: () -> Unit,onDetail: () -> Unit) {
    val row = state.feed ?: return
    ClubCard(Modifier.fillMaxWidth().testTag("home-friend-post")) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val avatarId = state.author?.text("avatarAssetId")
            if(avatarId != null) ClubPhoto(api,avatarId,"Фото автора",Modifier.size(36.dp).clip(CircleShape))
            else Avatar(row.getString("displayName"),null,Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(row.getString("displayName"),style = MaterialTheme.typography.titleMedium)
                Text(DiaryDates.display(row.getString("createdAt")),style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
            }
            IconButton(onDetail) { ClubGlyph(ClubIcon.CHEVRON,description = "Открыть запись друга") }
        }
        val photo = row.optJSONArray("mediaIds")?.optString(0)?.takeIf { it.isNotBlank() }
        Box(Modifier.fillMaxWidth().height(156.dp).clip(ClubTokens.ControlShape).clickable(onClick = onDetail)) {
            ClubPhoto(api,photo,"Фото в ленте",Modifier.fillMaxSize())
            // Uniform scrim keeps real photo content readable in BOTH themes.
            if(photo != null) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = .76f),androidx.compose.ui.graphics.Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val textColor = if(photo != null) androidx.compose.ui.graphics.Color.White else KraPivaColors.Cream
                Text(row.getString("positionName"),color = textColor,style = MaterialTheme.typography.titleMedium,maxLines = 2)
                Text(row.getString("breweryName"),color = textColor,style = MaterialTheme.typography.bodyMedium,maxLines = 1)
                row.text("styleName")?.let { Text(it,color = textColor,style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onLike,enabled = !state.likeBusy,modifier = Modifier.testTag("home-like"),contentPadding = PaddingValues(4.dp)) {
                ClubGlyph(ClubIcon.HEART,color = if(row.optBoolean("liked"))KraPivaColors.Amber else KraPivaColors.Cream,description = if(row.optBoolean("liked"))"Убрать лайк" else "Нравится")
                Spacer(Modifier.width(6.dp));Text(row.optInt("likeCount").toString(),color = KraPivaColors.Cream)
            }
            TextButton(onDetail,contentPadding = PaddingValues(4.dp)) {
                ClubGlyph(ClubIcon.COMMENT,description = "Комментарии");Spacer(Modifier.width(6.dp));Text(row.optInt("commentCount").toString(),color = KraPivaColors.Cream)
            }
            Spacer(Modifier.weight(1f));Rating(row)
        }
    }
}
@Composable private fun Rating(row: JSONObject) {
    if(row.isNull("ratingQuarters")) Text("Без оценки",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
    else Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("★",color = KraPivaColors.Amber,fontSize = 20.sp)
        Text(DiaryNumbers.rating(row.getInt("ratingQuarters")),style = MaterialTheme.typography.titleMedium)
    }
}
@Composable private fun Avatar(name: String,bitmap: Bitmap?,modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment = Alignment.Center) {
        if(bitmap != null) Image(bitmap.asImageBitmap(),"Фото профиля",Modifier.fillMaxSize(),contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(),fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.onPrimaryContainer,fontSize = 22.sp)
    }
}
@Composable private fun LoadingCard() {
    ClubCard(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Загрузка записей" }) {
        Box(Modifier.fillMaxWidth(.65f).height(18.dp).clip(ClubTokens.ControlShape).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth().height(64.dp).clip(ClubTokens.ControlShape).background(MaterialTheme.colorScheme.surfaceVariant))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
@Composable private fun StatusCard(title: String,body: String,action: String,onClick: () -> Unit) {
    ClubCard(Modifier.fillMaxWidth()) {
        Text(title,style = MaterialTheme.typography.titleMedium)
        Text(body,style = MaterialTheme.typography.bodyMedium,color = KraPivaColors.Muted)
        TextButton(onClick) { Text(action) }
    }
}
