package ru.krapiva.app.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*

/** Called only for a persisted server row; never for optimistic/local draft state. */
@Composable fun CheckinSuccess(data: JSONObject, api: AccountApi, onDetail: () -> Unit, onNew: () -> Unit, onFeed: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("checkin-success")) {
        ClubBeer(Modifier.fillMaxSize(),backdrop = true)
        Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) { TextButton(onDetail) { Text("Пропустить") } }
            ClubBrand()
            Spacer(Modifier.height(24.dp))
            ClubCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Text("✓",Modifier.align(Alignment.CenterHorizontally),style = MaterialTheme.typography.displaySmall,color = KraPivaColors.Amber)
                Text("Чекин сохранён!",Modifier.fillMaxWidth(),style = MaterialTheme.typography.headlineMedium,textAlign = TextAlign.Center)
                Text(ClubPhrases.forCheckin(data.getString("id")),Modifier.fillMaxWidth(),color = KraPivaColors.Muted,textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val photo = data.optJSONArray("mediaIds")?.optString(0)?.takeIf { it.isNotBlank() }
                    ClubPhoto(api, photo, "Фото сохранённого чекина", Modifier.size(84.dp), cacheOwn = true)
                    Column(Modifier.weight(1f)) {
                        Text(data.getString("positionName"),style = MaterialTheme.typography.titleLarge)
                        Text(if(data.isNull("ratingQuarters")) "Без оценки" else "Твоя оценка: ${DiaryNumbers.rating(data.getInt("ratingQuarters"))}",color = KraPivaColors.Muted)
                    }
                }
                DiaryButton("Посмотреть чекин",action = onDetail)
                OutlinedButton(onNew,Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Новый чекин") }
                TextButton(onFeed,Modifier.align(Alignment.CenterHorizontally)) { Text("В ленту") }
                Text("Чекин сохранён на сервере",Modifier.fillMaxWidth(),style = MaterialTheme.typography.bodySmall,textAlign = TextAlign.Center)
            }
        }
    }
}
