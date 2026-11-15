package ru.krapiva.app.badges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import ru.krapiva.app.designsystem.*

@Composable fun BadgesApp(api: AccountApi,onBack: ()->Unit,expired: ()->Unit,bottomBar: @Composable ()->Unit) {
    var items by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var offline by remember {mutableStateOf(false)}
    var category by rememberSaveable {mutableStateOf("Все категории")}
    var tab by rememberSaveable {mutableStateOf("all")};val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    suspend fun load(ids: List<String>?=null) {
        busy=true;error=null
        val requestOwner=api.ownerId()
        try {
            val response=if(ids==null) {
                val cached=api.catalogGet("v1/me/badges");offline=cached.second;cached.first
            } else {
                val confirmed=api.json("v1/me/badge-pins","PUT",JSONObject().put("ids",JSONArray(ids)))
                api.rememberBadges(requestOwner,confirmed);offline=false;confirmed
            }
            val received=response.getJSONArray("items").objects()
            if(api.ownerId()==requestOwner && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))items=received
        }
        catch(e: Exception) {if(e is CancellationException)throw e;if(e is ApiFailure&&e.status==401)expired();error=if(e is ApiFailure)e.message else "Достижения недоступны. Повторите запрос."}
        finally {busy=false}
    }
    LaunchedEffect(owner) {owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {try {load();awaitCancellation()}finally {items=emptyList()}}}
    BackHandler(onBack=onBack)
    val pins=items.filter {it.text("pinSlot")!=null}.sortedBy {it.getInt("pinSlot")}.map {it.getString("id")}
    KraPivaBackdrop {Column(Modifier.safeDrawingPadding()) {
        Row {TextButton(onBack){Text("Назад")};Text("Достижения",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(12.dp))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).testTag("badges-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Закреплено ${pins.size} из 3. Порядок виден в профиле.")
                Text("Прогресс рассчитан сервером по действующей истории. Удаление или скрытие записи может отозвать значок и закрепление.",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {for((value,label) in listOf("all" to "Все","earned" to "Получены","locked" to "Впереди"))FilterChip(tab==value,{tab=value},label={Text(label)})}
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (listOf("Все категории")+items.map{badgeCategory(it.optString("metric"))}.distinct()).forEach{label->FilterChip(category==label,{category=label},label={Text(label)})}
                }
                Text(if(offline)"Сохранённое подтверждённое состояние · до 24 часов. Прогресс и закрепления могли измениться." else "Статус подтверждён сервером при последней загрузке",color=KraPivaColors.Muted)
                error?.let {Text(it,color=KraPivaColors.Error)}
                TextButton({scope.launch {load()}},enabled=!busy){Text("Обновить")}
            }
            items(items.filter {(tab=="all"||(it.text("earnedAt")!=null)==(tab=="earned"))&&(category=="Все категории"||badgeCategory(it.optString("metric"))==category)},key={it.getString("id")}) {badge->
                val id=badge.getString("id");val slot=pins.indexOf(id);val earned=badge.text("earnedAt")!=null
                ClubCard {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {BadgeMedallion(earned);Column {Text(badgeCategory(badge.optString("metric")),color=KraPivaColors.Muted);Text(badge.getString("title"),style=MaterialTheme.typography.titleLarge)}}
                    Text(badge.getString("description"));Text("${badge.getInt("progress")} / ${badge.getInt("target")} · ${if(earned)"Получен" else "В процессе"}")
                    LinearProgressIndicator(progress={badgeFraction(badge.getInt("progress"),badge.getInt("target"))},modifier=Modifier.fillMaxWidth())
                    Text("Правило v${badge.getInt("version")}",style=MaterialTheme.typography.labelSmall,color=KraPivaColors.Muted)
                    if(earned) {
                        TextButton({scope.launch {load(if(slot>=0)pins.filter {it!=id}else pins+id)}},modifier=Modifier.testTag("badge-pin-$id"),enabled=!busy&&!offline&&(slot>=0||pins.size<3)) {Text(if(slot>=0)"Открепить · место ${slot+1}" else if(pins.size==3)"Закреплены три значка" else "Закрепить")}
                        if(slot>=0)Row {
                            fun move(to: Int) {val ordered=pins.toMutableList();ordered.removeAt(slot);ordered.add(to,id);scope.launch {load(ordered)}}
                            TextButton({move(slot-1)},enabled=!busy&&!offline&&slot>0){Text("Выше")};TextButton({move(slot+1)},enabled=!busy&&!offline&&slot<pins.lastIndex){Text("Ниже")}
                        }
                    }
                }
            }
            if(items.isNotEmpty()&&!busy&&items.none{(tab=="all"||(it.text("earnedAt")!=null)==(tab=="earned"))&&(category=="Все категории"||badgeCategory(it.optString("metric"))==category)})item{Text("В этой категории пока нет достижений")}
            if(items.isEmpty()&&!busy&&error==null)item {Text("Правила достижений ещё не опубликованы")}
        }
        bottomBar()
    }}
}

/** No progress of another user is requested: only intentionally pinned, earned badges. */
@Composable fun PinnedBadges(api: AccountApi,userId: String) {
    var rows by remember(userId) {mutableStateOf<List<JSONObject>>(emptyList())}
    var unavailable by remember(userId) {mutableStateOf(false)}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(userId,owner) {owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {try {unavailable=false;rows=api.json("v1/users/$userId/badges").getJSONArray("items").objects().filter {it.text("pinSlot")!=null}.sortedBy {it.getInt("pinSlot")}}
        catch(e: Exception){if(e is CancellationException)throw e;unavailable=true}
        try {awaitCancellation()}finally {rows=emptyList()}
    }}
    if(unavailable)Text("Закреплённые достижения недоступны",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
    for(row in rows)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {BadgeMedallion(true,pinned=true);Column {Text(row.getString("title"));Text(badgeCategory(row.optString("metric")),style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)}}
}
