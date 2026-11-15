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
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.DiaryNumbers
import java.time.ZoneId
import java.time.Year
import java.util.Locale

@Composable fun YearScreen(api:AccountApi) {
    var input by rememberSaveable{mutableStateOf(Year.now().value.toString())}
    var chosen by rememberSaveable{mutableIntStateOf(Year.now().value)}
    var zoneName by rememberSaveable{mutableStateOf(ZoneId.systemDefault().id)}
    var chosenZone by rememberSaveable{mutableStateOf(ZoneId.systemDefault().id)}
    var data by remember{mutableStateOf<AvailableData?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var share by remember{mutableStateOf(false)}
    LaunchedEffect(api){try{data=CachedPersonalSource(api).snapshot()}catch(e:Exception){if(e is CancellationException)throw e;error="Локальная история недоступна"}}
    val summary=remember(data,chosen,chosenZone){data?.let{PersonalAnalytics.summarize(it.checkins,ZoneId.of(chosenZone),chosen)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Мой год в KraPiva",style=MaterialTheme.typography.headlineMedium);Text(COVERAGE,color=KraPivaColors.Muted)
        OutlinedTextField(input,{input=it.take(4)},label={Text("Календарный год")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(zoneName,{zoneName=it.take(80)},label={Text("Часовой пояс, например Europe/Istanbul")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button({val year=input.toIntOrNull();val zone=runCatching{ZoneId.of(zoneName)}.getOrNull();if(year==null||year !in 1900..9999||zone==null)error="Укажите год 1900–9999 и существующий часовой пояс" else{chosen=year;chosenZone=zone.id;error=null}}){Text("Показать выбранный год")}
        error?.let{Text(it,color=KraPivaColors.Error)}
        summary?.let{s->
            Text("$chosen · $chosenZone",style=MaterialTheme.typography.titleLarge)
            ClubCard {Text("Чекинов: ${s.checkins}");Text("Уникальных позиций: ${s.uniquePositions}");Text("Средняя: ${s.average?.let{String.format(Locale.forLanguageTag("ru"),"%.2f",it)} ?: "Нет оценок"}")}
            if(s.checkins==0)Text("Нет доступных записей за выбранный год. Загрузите собственную историю или выберите другой год.")
            AnalyticsBars("Любимые стили · частота",s.styles);AnalyticsBars("Пивоварни",s.breweries)
            AnalyticsBars("Оценки",s.histogram.filter{it.second>0}.map{DiaryNumbers.rating(it.first) to it.second})
            Text("Высоко оценённые позиции",style=MaterialTheme.typography.titleLarge)
            s.top.forEach{Text("${it.name} · ${DiaryNumbers.rating(it.quarters!!)}")}
            Text("Достижения отображаются отдельно по подтверждённому серверному состоянию. Исторические даты присвоения в этот отчёт не подставляются.",color=KraPivaColors.Muted)
            Button({share=true},enabled=s.checkins>0){Text("Предпросмотр изображения года")}
            if(share)SharePreview("Мой $chosen в KraPiva",listOf("На основе доступных записей", "Часовой пояс: $chosenZone", "Чекинов: ${s.checkins} · позиций: ${s.uniquePositions}","Моя средняя: ${s.average?.let{String.format(Locale.forLanguageTag("ru"),"%.2f",it)} ?: "Нет оценок"}","Стиль: ${s.styles.firstOrNull()?.first ?: "Нет данных"}","Пивоварня: ${s.breweries.firstOrNull()?.first ?: "Нет данных"}"),onClose={share=false})
        }
    }
}
