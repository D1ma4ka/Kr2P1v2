package ru.krapiva.app.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.krapiva.app.designsystem.ClubCard
import java.time.Instant

private enum class NameAction { CREATE, RENAME, COPY }
private data class NameRequest(val action: NameAction, val id: String? = null, val name: String = "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MenuPresetControls(
    book: MenuPresetBook?,
    current: MenuFilter,
    busy: Boolean,
    apply: (String, MenuFilter) -> Unit,
    editFilter: (SavedMenuFilter) -> Unit,
    change: ((MenuPresetBook) -> MenuPresetBook) -> Unit,
) {
    var themes by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleting by remember { mutableStateOf<SavedMenuFilter?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ themes = true }) { Text("Готовые подборки") }
        OutlinedButton({ manage = true }, enabled = book != null) { Text("Мои фильтры") }
        TextButton({ nameRequest = NameRequest(NameAction.CREATE) }, enabled = book != null && !busy) {
            Text("Сохранить фильтр")
        }
    }
    if (book != null && book.recentIds.isNotEmpty()) {
        Text("Недавно применённые", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in book.recentIds) {
                val title = book.title(id) ?: continue
                val filter = book.filter(id) ?: continue
                SuggestionChip({ apply(id, filter) }, { Text(title) }, enabled = !busy)
            }
        }
    }
    if (themes) MenuPresetDialog("Готовые подборки", { themes = false }) {
        for (preset in themePresets) {
            ClubCard {
                TextButton({
                    themes = false
                    apply(preset.id, preset.filter)
                }, enabled = !busy) { Text(preset.name) }
                Text(preset.description)
            }
        }
    }
    if (manage && book != null) MenuPresetDialog("Мои фильтры", { manage = false }) {
        if (book.active.isEmpty()) Text("Сохраните текущие условия, чтобы применить их в другом баре.")
        for (preset in book.active) {
            ClubCard {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                if (preset.id == book.defaultId) Text("По умолчанию")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({
                        manage = false
                        apply(preset.id, preset.filter)
                    }, enabled = !busy) { Text("Применить") }
                    TextButton({
                        manage = false
                        editFilter(preset)
                    }, enabled = !busy) { Text("Условия") }
                    TextButton({
                        manage = false
                        nameRequest = NameRequest(NameAction.RENAME, preset.id, preset.name)
                    }, enabled = !busy) { Text("Переименовать") }
                    TextButton({
                        manage = false
                        nameRequest = NameRequest(NameAction.COPY, preset.id, (preset.name.take(72) + " · копия"))
                    }, enabled = !busy) { Text("Дублировать") }
                    TextButton({ change { it.selectDefault(preset.id) } }, enabled = !busy) {
                        Text(if (preset.id == book.defaultId) "Убрать по умолчанию" else "По умолчанию")
                    }
                    TextButton({ manage = false; deleting = preset }, enabled = !busy) { Text("Удалить") }
                }
            }
        }
    }
    nameRequest?.let { request ->
        PresetNameDialog(request, { nameRequest = null }) { name ->
            nameRequest = null
            val now = Instant.now()
            val newId = newMenuFilterId()
            change {
                when (request.action) {
                    NameAction.CREATE -> it.save(newId, name, current, now)
                    NameAction.RENAME -> it.rename(requireNotNull(request.id), name, now)
                    NameAction.COPY -> it.duplicate(requireNotNull(request.id), newId, name, now)
                }
            }
        }
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${preset.name}»?") },
            text = { Text("Сохранённые условия исчезнут из списка фильтров.") },
            dismissButton = { TextButton({ deleting = null }) { Text("Отмена") } },
            confirmButton = { TextButton({
                deleting = null
                change { it.delete(preset.id, Instant.now()) }
            }) { Text("Удалить") } },
        )
    }
}

@Composable
private fun MenuPresetDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
                TextButton(dismiss) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun PresetNameDialog(request: NameRequest, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var title by remember(request) { mutableStateOf(request.name) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(when (request.action) {
            NameAction.CREATE -> "Сохранить фильтр"
            NameAction.RENAME -> "Переименовать фильтр"
            NameAction.COPY -> "Дублировать фильтр"
        }) },
        text = { OutlinedTextField(title, { title = it.take(80) }, label = { Text("Название") }, singleLine = true) },
        dismissButton = { TextButton(dismiss) { Text("Отмена") } },
        confirmButton = { TextButton({ confirm(title) }, enabled = title.isNotBlank()) { Text("Сохранить") } },
    )
}
