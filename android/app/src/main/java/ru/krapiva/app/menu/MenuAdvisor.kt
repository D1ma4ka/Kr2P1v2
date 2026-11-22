package ru.krapiva.app.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.designsystem.ClubCard
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.diary.DiaryNumbers
import ru.krapiva.app.diary.objects
import ru.krapiva.app.offline.AdvisorCandidate
import ru.krapiva.app.offline.AdvisorCheckin
import ru.krapiva.app.offline.AdvisorMode
import ru.krapiva.app.offline.AdvisorProfile
import ru.krapiva.app.offline.AdvisorRecommendation
import ru.krapiva.app.offline.AdvisorWeights
import ru.krapiva.app.offline.RecommendationCard
import java.time.Instant

internal suspend fun AccountApi.advisorCheckins(): List<AdvisorCheckin> =
    readAdvisorCheckins(json("v1/me/advisor-history"))

internal fun readAdvisorCheckins(response: org.json.JSONObject): List<AdvisorCheckin> =
    response.getJSONArray("items").objects().map { row ->
        AdvisorCheckin(
            row.getString("id"), row.getString("positionId"), row.getString("breweryId"), row.text("styleId"),
            Instant.parse(row.getString("occurredAt")),
            if (row.isNull("ratingQuarters")) null else row.getInt("ratingQuarters"), row.getBoolean("firstForPosition"),
        )
    }

internal fun MenuPosition.advisorCandidate() = AdvisorCandidate(
    RecommendationCard(positionId, name, brewery, style), breweryId, styleId, tried, publicAverageRating,
)

internal data class MenuAdvice(val ranked: List<AdvisorRecommendation>, val scores: Map<String, Int>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvisorHeader(
    title: String,
    mode: AdvisorMode,
    profile: AdvisorProfile?,
    cached: Boolean,
    old: Boolean,
    selectMode: (AdvisorMode) -> Unit,
    refresh: () -> Unit,
) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    when (val size = profile?.historySize) {
        null -> if (profile == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Загружаем историю чекинов.", color = KraPivaColors.Muted)
        } else {
            Text("История недоступна. Показан нейтральный выбор из меню.", color = KraPivaColors.Muted)
        }
        else -> {
            Text("Учтено $size из последних 100 чекинов.", color = KraPivaColors.Muted)
            if (!profile.personalized) Text("Пока мало истории. Показан нейтральный выбор из меню.", color = KraPivaColors.Muted)
        }
    }
    if (cached) Text("Используется сохранённая редакция меню.", color = KraPivaColors.Muted)
    if (cached || old) Text("Наличие могло измениться. Уточните в заведении.", color = KraPivaColors.Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in AdvisorMode.entries) FilterChip(option == mode, { selectMode(option) }, { Text(option.title) })
    }
    if (profile != null) TextButton(refresh) { Text("Обновить рекомендации") }
}

@Composable
internal fun AdvisorPreview(
    mode: AdvisorMode,
    profile: AdvisorProfile?,
    advice: MenuAdvice?,
    positions: Map<String, MenuPosition>,
    cached: Boolean,
    old: Boolean,
    selectMode: (AdvisorMode) -> Unit,
    refresh: () -> Unit,
    showAll: () -> Unit,
    open: (String) -> Unit,
    checkin: (MenuPosition) -> Unit,
) {
    ClubCard {
        AdvisorHeader("Что мне попробовать?", mode, profile, cached, old, selectMode, refresh)
        val top = advice?.ranked?.take(AdvisorWeights.TOP).orEmpty()
        if (advice != null && top.isEmpty()) Text("Нет доступных позиций по этим условиям.")
        top.forEach { row ->
            val item = positions[row.candidate.card.id] ?: return@forEach
            key(item.positionId) {
                HorizontalDivider()
                AdvisorPosition(row, item, cached, open, checkin)
            }
        }
        if (top.isNotEmpty()) OutlinedButton(showAll) { Text("Все рекомендации") }
    }
}

@Composable
internal fun AdvisorScreen(
    venueName: String,
    mode: AdvisorMode,
    profile: AdvisorProfile?,
    advice: MenuAdvice?,
    positions: Map<String, MenuPosition>,
    cached: Boolean,
    old: Boolean,
    selectMode: (AdvisorMode) -> Unit,
    refresh: () -> Unit,
    back: () -> Unit,
    open: (String) -> Unit,
    checkin: (MenuPosition) -> Unit,
) {
    BackHandler(onBack = back)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(back) { Text("К меню") }
            AdvisorHeader("Рекомендации · $venueName", mode, profile, cached, old, selectMode, refresh)
            if (advice?.ranked?.isEmpty() == true) Text("Нет доступных позиций по этим условиям.")
        }
        items(advice?.ranked.orEmpty(), key = { it.candidate.card.id }) { row ->
            val item = positions[row.candidate.card.id] ?: return@items
            ClubCard { AdvisorPosition(row, item, cached, open, checkin) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvisorPosition(
    recommendation: AdvisorRecommendation,
    position: MenuPosition,
    cached: Boolean,
    open: (String) -> Unit,
    checkin: (MenuPosition) -> Unit,
) {
    var explain by rememberSaveable(position.positionId) { mutableStateOf(false) }
    Text(position.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    Text(listOfNotNull(position.brewery, position.style, position.presentation()).joinToString(" · "))
    val facts = listOfNotNull(
        position.abv?.let { "${it}%" },
        position.publicAverageRating?.let { "Публичный рейтинг ${DiaryNumbers.average(it.toString())}" },
        when {
            recommendation.tried -> "Пробовал"
            cached -> "Нет чекинов в сохранённой редакции"
            else -> "Не пробовал"
        },
    )
    Text(facts.joinToString(" · "), color = KraPivaColors.Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton({ explain = !explain }) { Text(if (explain) "Скрыть объяснение" else "Почему советуем?") }
        TextButton({ open(position.positionId) }) { Text("Карточка") }
        TextButton({ checkin(position) }) { Text("Чекин") }
    }
    if (explain) {
        recommendation.explanation.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
