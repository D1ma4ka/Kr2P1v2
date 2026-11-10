package ru.krapiva.app.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.ApiFailure
import java.util.UUID

@Composable fun ReportAction(api: AccountApi,type: String,id: String,expired: ()->Unit={}) {
    var open by rememberSaveable(type,id) {mutableStateOf(false)}
    TextButton({open=true}) {Text("Пожаловаться")}
    if(open)ReportDialog(api,type,id,expired){open=false}
}
@Composable private fun ReportDialog(api: AccountApi,type: String,id: String,expired: ()->Unit,close: ()->Unit) {
    var reason by rememberSaveable {mutableStateOf("")};var request by rememberSaveable {mutableStateOf(UUID.randomUUID().toString())}
    var pending by rememberSaveable {mutableStateOf(false)};var busy by remember {mutableStateOf(false)}
    var result by rememberSaveable {mutableStateOf<String?>(null)};var sent by rememberSaveable {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Жалоба на публичный контент")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Опишите причину. Решение примет модератор.")
        if(!sent)OutlinedTextField(reason,{reason=it.take(2000)},enabled=!busy&&!pending,label={Text("Причина жалобы")},modifier=Modifier.fillMaxWidth())
        result?.let {Text(it)}
    }},confirmButton={if(!sent)TextButton({
        busy=true;pending=true
        scope.launch {try {
            api.json("v1/reports/$request","PUT",JSONObject().put("subjectType",type).put("subjectId",id).put("reason",reason.trim()))
            sent=true;result="Жалоба принята сервером"
        } catch(e: Exception) {if(e is CancellationException)throw e
            if(e is ApiFailure&&e.status==401)expired()
            result=if(e is ApiFailure)e.message else "Нет подтверждения отправки. Повторите с тем же текстом."
            if(e is ApiFailure&&e.status in setOf(400,404,422)) {pending=false;request=UUID.randomUUID().toString()}
        } finally {busy=false}}
    },enabled=!busy&&reason.isNotBlank()) {Text(if(pending)"Повторить" else "Отправить")}},dismissButton={TextButton(close,enabled=!busy){Text(if(sent)"Закрыть" else "Отмена")}})
}
