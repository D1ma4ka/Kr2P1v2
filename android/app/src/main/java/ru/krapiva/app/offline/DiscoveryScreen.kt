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
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*

@Composable fun DiscoveryScreen(api: AccountApi, openPosition: (String)->Unit, openCatalog: ()->Unit) {
    var data by remember{mutableStateOf<AvailableData?>(null)};var error by remember{mutableStateOf(false)}
    var reload by remember{mutableIntStateOf(0)}
    LaunchedEffect(api,reload){try{error=false;data=CachedPersonalSource(api).snapshot()}catch(e:Exception){if(e is CancellationException)throw e;error=true}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Открытия",style=MaterialTheme.typography.headlineMedium);Text(COVERAGE,color=KraPivaColors.Muted)
        Text("Локальный подбор из доступных карточек. Рейтинг сообщества и популярность не рассчитываются.",color=KraPivaColors.Muted)
        TextButton({reload++}){Text("Обновить локальный подбор")}
        if(error)Text("Данные на устройстве недоступны. Повторите попытку.",color=KraPivaColors.Error)
        data?.let { source ->
            val cards=source.positions.map{RecommendationCard(it.getString("id"),it.getString("name"),it.text("breweryName"),it.text("styleName"))}
            val recommendations=LocalDiscovery.select(cards,source.checkins)
            @Composable fun block(title:String,items:List<Pair<RecommendationCard,String>>) {
                ClubCard {
                    Text(title,style=MaterialTheme.typography.titleLarge)
                    if(items.isEmpty())Text("Пока недостаточно доступных данных. Откройте каталог и свои записи.",color=KraPivaColors.Muted)
                    items.take(5).forEach{(card,reason)->TextButton({openPosition(card.id)}){Column{Text(card.name);Text(reason,style=MaterialTheme.typography.bodySmall)}}}
                }
            }
            block("Для тебя",recommendations.take(3).map{it.card to it.reason})
            block("Из любимых стилей",recommendations.filter{it.card.style in source.checkins.filter{r->(r.quarters ?: 0)>=16}.mapNotNull{r->r.style}}.map{it.card to (it.card.style ?: "")})
            block("Мои недавние находки",source.checkins.sortedByDescending{it.occurredAt}.distinctBy{it.positionId}.take(5).map{RecommendationCard(it.positionId,it.name,it.brewery,it.style) to "Из моих доступных записей"})
            block("Хочу попробовать",cards.filter{it.id in source.wishlist}.map{it to "Из доступной копии wishlist"})
            block("Может понравиться",recommendations.drop(3).map{it.card to it.reason})
        } ?: run { if(!error)LinearProgressIndicator(Modifier.fillMaxWidth()) }
        Button(openCatalog){Text("Перейти в каталог")}
    }
}
