package ru.krapiva.app.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.*

@Composable fun ClubCheckinCard(row: JSONObject,api: AccountApi,offline: Boolean = false,enabled: Boolean = true,onClick: () -> Unit) {
    ClubCard(Modifier.fillMaxWidth().clickable(enabled = enabled,onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClubPhoto(api,row.optJSONArray("mediaIds")?.optString(0),"Фото чекина",Modifier.width(112.dp).height(140.dp),cacheOwn = true,offline = offline)
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.getString("positionName"),style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
                Text(row.getString("breweryName"),color = KraPivaColors.Muted,style = MaterialTheme.typography.bodyMedium)
                row.text("styleName")?.let { Text(it,color = KraPivaColors.Muted,style = MaterialTheme.typography.bodySmall) }
                Text(if(row.isNull("ratingQuarters"))"Без оценки" else "★ ${DiaryNumbers.rating(row.getInt("ratingQuarters"))}",color = KraPivaColors.Amber,style = MaterialTheme.typography.titleMedium)
                Text(DiaryDates.display(row.getString("occurredAt")),style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted)
                row.text("body")?.let { Text(it,maxLines = 2,overflow = TextOverflow.Ellipsis,fontStyle = FontStyle.Italic,style = MaterialTheme.typography.bodyMedium) }
            }
        }
        row.text("visibility")?.let { Text(when(it) { "friends" -> "Для друзей";"public" -> "Для всех";else -> "Только я" },style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Muted) }
    }
}
