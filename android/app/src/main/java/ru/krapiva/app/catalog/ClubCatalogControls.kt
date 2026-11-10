package ru.krapiva.app.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.designsystem.*
import ru.krapiva.app.diary.objects
import ru.krapiva.app.diary.DiaryNumbers

@Composable internal fun CatalogRating(api: AccountApi,id: String,offline: Boolean) {
    var data by remember(api,id,offline) {mutableStateOf<org.json.JSONObject?>(null)}
    var failed by remember(api,id,offline) {mutableStateOf(false)}
    LaunchedEffect(api,id,offline) {
        try {data=api.catalogGet("v1/positions/$id/statistics").first}
        catch(e: Exception) {if(e is CancellationException)throw e;failed=true}
    }
    val stats=data
    when {
        stats != null -> {
            Text(if(stats.isNull("averageRating")) "Нет оценок" else "★ ${DiaryNumbers.average(stats.text("averageRating"))}",color=KraPivaColors.Amber,style=MaterialTheme.typography.titleMedium)
            Text("По доступным вам записям" + if(offline) " · сохранено" else "",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
        }
        failed -> Text("Оценка недоступна",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
        else -> Text("Загрузка оценки…",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
    }
}

@Composable internal fun StyleChips(state: CatalogState,model: CatalogViewModel) {
    var styles by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    LaunchedEffect(model) { try { styles = model.repository.read("v1/styles?limit=5").first.getJSONArray("items").objects() } catch(e: Exception) { if(e is CancellationException)throw e } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.filters.styleId.isBlank(),{ model.filters(state.filters.copy(styleId = "",styleLabel = ""));model.refresh() },label = { Text("Все") },enabled = !state.busy)
        for(style in styles) FilterChip(state.filters.styleId == style.getString("id"),{
            model.filters(state.filters.copy(styleId = style.getString("id"),styleLabel = style.getString("name")));model.refresh()
        },label = { Text(style.getString("name")) },enabled = !state.busy)
        if(state.filters.styleId.isNotBlank() && styles.none { it.getString("id") == state.filters.styleId }) FilterChip(true,{ model.open(CatalogPage.STYLES) },label = { Text(state.filters.styleLabel) },enabled = !state.busy)
    }
}

/** No optimistic bookmark: both initial value and changes are confirmed by the real API. */
@Composable fun WishlistMark(api: AccountApi,id: String,offline: Boolean) {
    var wished by remember(api,id) { mutableStateOf(false) }
    var busy by remember(api,id) { mutableStateOf(true) }
    var known by remember(api,id) { mutableStateOf(false) }
    var failed by remember(api,id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var revision by remember(api,id) {mutableIntStateOf(0)}
    LaunchedEffect(api,id,offline,revision) {
        try { wished = api.catalogGet("v1/me/wishlist/$id").first.getBoolean("wished");known = true }
        catch(e: Exception) { if(e is CancellationException) throw e;failed = true }
        finally { busy = false }
    }
    Column(Modifier.widthIn(max = 80.dp)) {
        IconButton({
            busy = true;scope.launch {
                try { wished = api.json("v1/me/wishlist/$id",if(wished)"DELETE" else "PUT").getBoolean("wished");api.invalidateDiary();failed = false }
                catch(e: Exception) { if(e is CancellationException) throw e;failed = true }
                finally { busy = false }
            }
        },enabled = known && !busy && !offline) {
            ClubGlyph(ClubIcon.BOOKMARK,color = if(wished)KraPivaColors.Amber else KraPivaColors.Cream,description = if(wished) "Убрать из wishlist" else "Хочу попробовать")
        }
        if(failed) TextButton({busy=true;failed=false;revision++},enabled = !busy) {Text("Повторить",style = MaterialTheme.typography.bodySmall,color = KraPivaColors.Error)}
    }
}
