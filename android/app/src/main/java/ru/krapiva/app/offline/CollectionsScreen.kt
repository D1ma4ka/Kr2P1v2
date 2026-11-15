package ru.krapiva.app.offline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.objects

@Composable fun CollectionsScreen(api: AccountApi, openPosition: (String) -> Unit, openCatalog: () -> Unit) {
    val repository = remember(api) { CollectionsRepository(api) }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var labels by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var name by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun action(block: suspend () -> Unit) {
        if(busy) return
        busy = true
        scope.launch { try { block();rows=repository.all();labels=availablePositionLabels(api);error=null }
            catch(e: Exception) { if(e is CancellationException)throw e;error=e.message ?: "Не удалось сохранить подборку" }
            finally { busy=false } }
    }
    LaunchedEffect(api) { action {} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Личные подборки",style=MaterialTheme.typography.headlineMedium)
        Text("Хранятся на этом устройстве для вашего аккаунта. Синхронизация ещё не подключена.",color=KraPivaColors.Muted)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=KraPivaColors.Error) }
        OutlinedTextField(name,{name=it.take(80)},label={Text("Название новой подборки")},modifier=Modifier.fillMaxWidth())
        Button({action {repository.create(name);name=""}},enabled=!busy&&name.isNotBlank()){Text("Создать подборку")}
        TextButton(openCatalog){Text("Добавить позиции из каталога")}
        if(rows.isEmpty()&&!busy)Text("Создайте подборку и добавляйте в неё существующие позиции из карточек каталога.")
        rows.forEach { row ->
            val id=row.getString("id");val members=row.getJSONArray("members").objects().filter{!it.optBoolean("deleted")}
            ClubCard {
                TextButton({selected=if(selected==id)null else id;rename=row.getString("name")}){Text("${row.getString("name")} · ${members.size}")}
                if(selected==id) {
                    OutlinedTextField(rename,{rename=it.take(80)},label={Text("Изменить название")},modifier=Modifier.fillMaxWidth())
                    TextButton({action {repository.rename(id,rename)}},enabled=!busy&&rename.isNotBlank()){Text("Сохранить название")}
                    TextButton({deleting=row},enabled=!busy){Text("Удалить подборку")}
                    if(members.isEmpty())Text("В подборке пока нет позиций")
                    members.forEachIndexed { index, member ->
                        val positionId=member.getString("positionId")
                        HorizontalDivider()
                        TextButton({openPosition(positionId)}){Text(labels[positionId] ?: "Позиция ${index+1} · карточка не загружена")}
                        Text("Карточка откроется с проверкой доступности каталога.",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
                        Row {
                            TextButton({action {repository.reorder(id,positionId,-1)}},enabled=!busy&&index>0){Text("Выше")}
                            TextButton({action {repository.reorder(id,positionId,1)}},enabled=!busy&&index<members.lastIndex){Text("Ниже")}
                            TextButton({action {repository.remove(id,positionId)}},enabled=!busy){Text("Убрать")}
                        }
                        rows.filter{it.getString("id")!=id}.forEach { target ->
                            TextButton({action {repository.move(id,target.getString("id"),positionId,false)}},enabled=!busy){Text("Перенести в «${target.getString("name")}»")}
                            TextButton({action {repository.move(id,target.getString("id"),positionId,true)}},enabled=!busy){Text("Также добавить в «${target.getString("name")}»")}
                        }
                    }
                }
            }
        }
    }
    deleting?.let { row -> AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить «${row.getString("name")}»?")},text={Text("Будет удалена только подборка. Чекины и wishlist сохранятся.")},confirmButton={TextButton({deleting=null;action {repository.delete(row.getString("id"))}},enabled=!busy){Text("Удалить")}},dismissButton={TextButton({deleting=null}){Text("Отмена")}}) }
}

@Composable fun AddToCollection(api: AccountApi, positionId: String) {
    var dialog by remember {mutableStateOf(false)}
    var rows by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var name by rememberSaveable {mutableStateOf("")}
    var message by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val repo=remember(api){CollectionsRepository(api)};val scope=rememberCoroutineScope()
    fun action(block: suspend ()->Unit) {if(busy)return;busy=true;scope.launch {try{block();rows=repo.all();message=null}catch(e:Exception){if(e is CancellationException)throw e;message=e.message ?: "Не удалось сохранить"}finally{busy=false}}}
    TextButton({dialog=true;action{}},enabled=!busy){Text("Добавить в личную подборку")}
    if(dialog) AlertDialog(onDismissRequest={dialog=false},title={Text("Личные подборки · на устройстве")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            message?.let{Text(it,color=KraPivaColors.Error)}
            rows.forEach {row ->
                val added=row.getJSONArray("members").objects().any{it.getString("positionId")==positionId&&!it.optBoolean("deleted")}
                TextButton({action{repo.add(row.getString("id"),positionId)}},enabled=!busy&&!added){Text(row.getString("name")+if(added)" · добавлено" else "")}
            }
            OutlinedTextField(name,{name=it.take(80)},label={Text("Новая подборка")})
            TextButton({action{repo.create(name);name=""}},enabled=!busy&&name.isNotBlank()){Text("Создать")}
        }
    },confirmButton={TextButton({dialog=false}){Text("Готово")}})
}
