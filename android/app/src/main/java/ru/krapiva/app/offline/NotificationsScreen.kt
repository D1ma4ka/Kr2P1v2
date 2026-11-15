package ru.krapiva.app.offline

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.objects
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable fun NotificationsScreen(api:AccountApi,open:(String)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val repo=remember(api){ReminderRepository(api)}
    var data by remember{mutableStateOf(JSONObject())};var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var kind by remember{mutableStateOf("DRAFTS")};var delayHours by remember{mutableIntStateOf(24)}
    fun action(block:suspend ()->Unit){if(busy)return;busy=true;scope.launch{try{block();data=repo.read()}catch(e:Exception){if(e is CancellationException)throw e;message=e.message ?: "Не удалось сохранить напоминание"}finally{busy=false}}}
    fun schedule(){action{
        val id=UUID.randomUUID().toString();val owner=api.ownerId() ?: error("Требуется вход")
        val due=System.currentTimeMillis()+delayHours*3_600_000L
        repo.enabled(true)
        ReminderJobs.schedule(context,owner,id,kind,due)
        try{repo.add(id,kind,due)}catch(e:Exception){ReminderJobs.cancel(context,owner,id);throw e}
        message="Напоминание запланировано. Android может отложить его в режиме энергосбережения."
    }}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)schedule()else message="Разрешение не выдано. Системное напоминание не запланировано."}
    LaunchedEffect(api){action{}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Уведомления",style=MaterialTheme.typography.headlineMedium)
        Text("Только ваши локальные напоминания. Социальные события и push ещё не подключены.",color=KraPivaColors.Muted)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());message?.let{Text(it,color=KraPivaColors.Amber)}
        Text("Настройки напоминаний",style=MaterialTheme.typography.titleLarge)
        Row {Switch(data.optBoolean("enabled"),{enabled->action{repo.enabled(enabled);if(!enabled)api.ownerId()?.let{ReminderJobs.cancel(context,it)}}},enabled=!busy);Text("Включены на этом устройстве")}
        Text("Добавление напоминания — явное действие. Разрешение Android запрашивается только при планировании.",color=KraPivaColors.Muted)
        for((value,label)in listOf("DRAFTS" to "Вернуться к черновикам","COLLECTIONS" to "Вернуться к подборкам"))FilterChip(kind==value,{kind=value},label={Text(label)})
        Row {FilterChip(delayHours==1,{delayHours=1},label={Text("Через час")});Spacer(Modifier.width(8.dp));FilterChip(delayHours==24,{delayHours=24},label={Text("Через сутки")})}
        Button({
            if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if(ReminderJobs.allowed(context))schedule()
            else message="Уведомления отключены в Android. Разрешите их в настройках приложения и повторите."
        },enabled=!busy){Text("Запланировать выбранное напоминание")}
        TextButton({action{}},enabled=!busy){Text("Обновить центр уведомлений")}
        val rows=data.optJSONArray("items")?.objects().orEmpty().filter{!it.optBoolean("deleted")&&!it.optBoolean("cancelled")}.sortedByDescending{it.optLong("dueAt")}
        if(rows.isEmpty())Text("Нет уведомлений. Добавьте напоминание, когда оно понадобится.",color=KraPivaColors.Muted)
        rows.forEach{row->
            val due=row.getLong("dueAt");val pending=due>System.currentTimeMillis()
            ClubCard {
                Text(if(row.getString("kind")=="DRAFTS")"Вернуться к черновикам" else "Вернуться к подборкам",style=MaterialTheme.typography.titleMedium)
                Text(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                Text(if(pending)"Запланировано" else if(row.optBoolean("read"))"Прочитано" else "Не прочитано",color=KraPivaColors.Muted)
                TextButton({action{repo.change(row.getString("id"));open(row.getString("kind"))}},enabled=!busy){Text("Открыть раздел")}
                if(!pending&&!row.optBoolean("read"))TextButton({action{repo.change(row.getString("id"))}},enabled=!busy){Text("Отметить прочитанным")}
                TextButton({action{repo.change(row.getString("id"),true);api.ownerId()?.let{ReminderJobs.cancel(context,it,row.getString("id"))}}},enabled=!busy){Text(if(pending)"Отменить и удалить" else "Удалить уведомление")}
            }
        }
    }
}
