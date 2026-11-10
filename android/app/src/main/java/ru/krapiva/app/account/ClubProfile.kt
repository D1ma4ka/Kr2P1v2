package ru.krapiva.app.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.*

@Composable fun ClubProfile(state: AccountState,model: AccountViewModel,bottomBar: @Composable () -> Unit) {
    val p=state.profile ?: return
    var about by rememberSaveable(p.getString("userId")) { mutableStateOf(false) }
    var home by remember { mutableStateOf<JSONObject?>(null) }
    var friends by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(p) {
        try { val result=model.api.catalogGet("v1/me/home");home=result.first;offline=result.second }
        catch(e: Exception) { if(e is CancellationException)throw e;error=true }
        try { val result=model.api.json("v1/friends?limit=50");friends=result.getJSONArray("items").length().toString()+if(result.text("nextCursor")!=null)"+" else "" }
        catch(e: Exception) { if(e is CancellationException)throw e;friends=null }
    }
    Surface(Modifier.fillMaxSize(),color=KraPivaColors.Ink) {
        Column(Modifier.safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    ClubBrand();IconButton({ model.navigate(AccountScreen.SETTINGS) }) { ClubGlyph(ClubIcon.SETTINGS,description="Настройки аккаунта") }
                } }
                state.error?.let { item { Text(it,color=KraPivaColors.Error) } }
                state.notice?.let { item { Text(it) } }
                if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    state.cover?.let { Image(it.asImageBitmap(),"Обложка профиля",Modifier.fillMaxWidth().height(160.dp).clip(ClubTokens.CardShape).clickable { model.viewPhoto("coverAssetId") },contentScale=ContentScale.Crop) }
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled=state.avatar!=null) { model.viewPhoto("avatarAssetId") },contentAlignment=Alignment.Center) {
                            state.avatar?.let { Image(it.asImageBitmap(),"Аватар профиля",Modifier.fillMaxSize(),contentScale=ContentScale.Crop) } ?: ClubGlyph(ClubIcon.PERSON,Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(p.getString("displayName"),style=MaterialTheme.typography.headlineSmall)
                            val location=listOfNotNull(p.text("city"),p.text("country")).joinToString(", ")
                            if(location.isNotBlank()) Text(location,color=KraPivaColors.Muted)
                            p.text("bio")?.let { Text(it,maxLines=3,color=KraPivaColors.Muted) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ model.navigate(AccountScreen.EDIT) },Modifier.weight(1f).heightIn(min=48.dp),enabled=!state.busy&&!state.offline) { Text("Редактировать профиль") }
                        AmberAction("Чекин",{ model.openDiary("new") },enabled=!state.busy)
                    }
                }
                if(offline || state.offline) item { Text("Сохранённая копия · изменения требуют сети",color=KraPivaColors.Amber) }
                item { ClubCard {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        val stats=home?.optJSONObject("statistics")
                        for((value,label,action) in listOf(Triple(stats?.optInt("totalCheckins")?.toString() ?: "—","чекинов",{model.openDiary("DIARY")}),Triple(stats?.optInt("uniquePositions")?.toString() ?: "—","сортов",{model.openDiary("UNIQUE")}),Triple(friends ?: "—","друзей",model::openFriends))) {
                            Column(Modifier.weight(1f).heightIn(min=64.dp).clickable(onClick=action),horizontalAlignment=Alignment.CenterHorizontally) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    ClubGlyph(when(label) {"чекинов"->ClubIcon.GLASS;"сортов"->ClubIcon.BOTTLE;else->ClubIcon.PEOPLE},Modifier.size(22.dp))
                                    Text(value,style=MaterialTheme.typography.headlineMedium)
                                }
                                Text(label,color=KraPivaColors.Muted)
                            }
                        }
                    }
                } }
                if(error) item { Text("Статистика недоступна. Откройте раздел и повторите запрос.",color=KraPivaColors.Muted) }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!about,{about=false},label={Text("Мои записи")});FilterChip(about,{about=true},label={Text("О себе")})
                } }
                if(about) item {
                    ClubCard {
                        Text(listOfNotNull(p.text("firstName"),p.text("lastName")).joinToString(" "),style=MaterialTheme.typography.titleLarge)
                        p.text("bio")?.let { Text(it) }
                        p.text("gender")?.let { Text("Пол: $it") }
                        Text("Публичность: ${when(p.text("profileVisibility")){"public"->"Все пользователи";"friends"->"Друзья";else->"Только я"}}",color=KraPivaColors.Muted)
                        val handler=LocalUriHandler.current
                        var failed by remember {mutableStateOf(false)}
                        for(link in p.optJSONArray("links")?.objects().orEmpty()) TextButton({failed=runCatching {handler.openUri(link.getString("url"))}.isFailure}) {Text(link.getString("url"))}
                        if(failed) Text("Не удалось открыть ссылку",color=KraPivaColors.Error)
                    }
                } else {
                    val rows=home?.optJSONObject("recent")?.optJSONArray("items")?.objects().orEmpty()
                    items(rows,key={it.getString("id")}) { row -> ClubCheckinCard(row,model.api,offline){model.openOwnCheckin(row.getString("id"))} }
                    if(rows.isEmpty()) item { Text(if(home==null) "Записи ещё не загружены" else "Коллекция пока пуста. Сохрани первое впечатление.",color=KraPivaColors.Muted) }
                }
                item {
                    TextButton({model.openDiary("DIARY")}) {Text("Все мои записи")}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton({model.openDiary("STATISTICS")}) {Text("Статистика")}
                        TextButton({model.openDiary("UNIQUE")}) {Text("Уникальные")}
                        TextButton({model.openDiary("WISHLIST")}) {Text("Wishlist")}
                    }
                    TextButton({model.openSocial()},enabled=!state.offline) {Text("Друзья и лента")}
                    TextButton(model::refresh,enabled=!state.busy) {Text("Обновить")}
                    TextButton({model.navigate(AccountScreen.SETTINGS)}) {Text("Настройки аккаунта")}
                }
            }
            bottomBar()
        }
    }
    state.fullPhoto?.let { photo -> AlertDialog(onDismissRequest=model::closePhoto,title={Text("Фото профиля")},text={Image(photo.asImageBitmap(),"Просмотр фото",Modifier.fillMaxWidth())},confirmButton={TextButton(model::closePhoto){Text("Закрыть")}}) }
}
