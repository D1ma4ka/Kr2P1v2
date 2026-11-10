package ru.krapiva.app.places

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects

@Composable fun VenuePicker(api: AccountApi,select: (JSONObject)->Unit,close: ()->Unit) {
    var q by rememberSaveable {mutableStateOf("")};var city by rememberSaveable {mutableStateOf("")}
    var rows by remember {mutableStateOf<List<JSONObject>>(emptyList())};var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)};var next by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    suspend fun load(more: Boolean=false) {
        busy=true;error=null
        try {val data=api.json("v1/venues?limit=20&q=${Uri.encode(q)}&city=${Uri.encode(city)}"+(if(more)"&cursor=${Uri.encode(next)}" else ""))
            rows=((if(more)rows else emptyList())+data.getJSONArray("items").objects()).distinctBy {it.getString("id")};next=data.text("nextCursor")
        }catch(e: Exception){if(e is CancellationException)throw e;error="Не удалось загрузить места. Можно указать неподтверждённый текст вручную."}finally {busy=false}
    }
    LaunchedEffect(q,city) {rows=emptyList();next=null;delay(800);load()}
    AlertDialog(onDismissRequest=close,title={Text("Выбрать известное место")},text={Column {
        OutlinedTextField(q,{q=it.take(120)},enabled=!busy,label={Text("Название или адрес")})
        OutlinedTextField(city,{city=it.take(120)},enabled=!busy,label={Text("Город (необязательно)")})
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it)}
        LazyColumn(Modifier.heightIn(max=300.dp)) {
            items(rows,key={it.getString("id")}) {row->TextButton({select(row)}){Column {Text(row.getString("name"));Text(listOfNotNull(row.text("city"),row.text("address")).joinToString(" · "));if(row.optBoolean("testOnly"))Text("Только тестовая запись")}}}
            if(rows.isEmpty()&&!busy)item {Text("Нет подходящих мест")}
            if(next!=null)item {TextButton({scope.launch {load(true)}},enabled=!busy){Text("Загрузить ещё")}}
        }
    }},confirmButton={TextButton({scope.launch {load()}},enabled=!busy){Text("Повторить поиск")}},dismissButton={TextButton(close){Text("Закрыть")}})
}
