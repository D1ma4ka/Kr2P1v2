package ru.krapiva.app.offline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.DiaryNumbers
import java.time.ZoneId
import java.util.Locale

internal const val COVERAGE = "На основе доступных записей · кэш до 24 часов. Это может быть часть истории; изменения на сервере ещё не получены."
@Composable internal fun AnalyticsBars(title: String, values: List<Pair<String,Int>>) {
    Text(title,style=MaterialTheme.typography.titleLarge)
    val maximum=values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    values.take(12).forEach { (label,count) ->
        Text("$label · $count")
        LinearProgressIndicator(progress={count.toFloat()/maximum},modifier=Modifier.fillMaxWidth())
    }
    if(values.isEmpty())Text("Нет доступных данных",color=KraPivaColors.Muted)
}
@Composable fun TasteScreen(api: AccountApi) {
    var summary by remember {mutableStateOf<TasteSummary?>(null)}
    var error by remember {mutableStateOf(false)};var reload by remember {mutableIntStateOf(0)}
    LaunchedEffect(api,reload) {try {error=false;summary=PersonalAnalytics.summarize(CachedPersonalSource(api).snapshot().checkins,ZoneId.systemDefault())}catch(e:Exception){if(e is CancellationException)throw e;error=true}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Мой вкус",style=MaterialTheme.typography.headlineMedium);Text(COVERAGE,color=KraPivaColors.Muted)
        TextButton({reload++}){Text("Пересчитать по локальным данным")}
        if(error)Text("Не удалось прочитать локальную историю. Повторите попытку.",color=KraPivaColors.Error)
        summary?.let { s ->
            if(s.checkins==0)Text("Здесь появится ваш вкус после загрузки собственных записей в разделе «Мои записи». Оценки без заполненного значения не учитываются.")
            ClubCard {
                Text("Доступно чекинов: ${s.checkins} · уникальных позиций: ${s.uniquePositions}")
                Text("Моя средняя оценка: ${s.average?.let { String.format(Locale.forLanguageTag("ru"),"%.2f",it) } ?: "Нет оценок"}")
            }
            AnalyticsBars("Стили · частота в моих записях",s.styles)
            AnalyticsBars("Пивоварни · частота в моих записях",s.breweries)
            AnalyticsBars("Вкусы · мои теги",s.tags)
            AnalyticsBars("Мои оценки · только заполненные",s.histogram.filter{it.second>0}.map{DiaryNumbers.rating(it.first) to it.second})
            Text("Предпочтения во времени",style=MaterialTheme.typography.titleLarge)
            if(s.monthlyStyles.isEmpty())Text("Нужно хотя бы пять доступных записей за два разных месяца.",color=KraPivaColors.Muted)
            s.monthlyStyles.forEach{(month,styles)->Text("$month · ${styles.firstOrNull()?.let{"${it.first}: ${it.second}"} ?: "Стиль неизвестен"}")}
        } ?: run { if(!error)LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}
