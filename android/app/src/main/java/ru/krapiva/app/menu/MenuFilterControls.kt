package ru.krapiva.app.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject

internal val menuFilterSaver = Saver<MenuFilter, String>(
    save = { it.encode().toString() },
    restore = { runCatching { MenuFilter.decode(JSONObject(it)) }.getOrNull() },
)

internal data class FilterChipAction(val label: String, val cleared: MenuFilter)

internal fun MenuFilter.chips(): List<FilterChipAction> = buildList {
    fun addCondition(active: Boolean, label: String, cleared: MenuFilter) {
        if (active) add(FilterChipAction(label, cleared))
    }
    addCondition(query.isNotBlank(), "Поиск: $query", copy(query = ""))
    addCondition(families.isNotEmpty() || styles.isNotEmpty(), "Стили (${families.size + styles.size})", copy(families = emptySet(), styles = emptySet()))
    addCondition(breweries.isNotEmpty(), "Пивоварни (${breweries.size})", copy(breweries = emptySet()))
    addCondition(countries.isNotEmpty(), "Страны (${countries.size})", copy(countries = emptySet()))
    addCondition(formats.isNotEmpty(), "Подача или упаковка (${formats.size})", copy(formats = emptySet()))
    addCondition(tried != Tried.ALL, tried.title, copy(tried = Tried.ALL))
    addCondition(abv.active, "ABV", copy(abv = MenuRange()))
    addCondition(volumeMl.active, "Объём", copy(volumeMl = MenuRange()))
    addCondition(ibu.active, "IBU", copy(ibu = MenuRange()))
    addCondition(ratingMin.isNotBlank(), "Рейтинг от $ratingMin", copy(ratingMin = ""))
    addCondition(price.active, "Цена ${currency.orEmpty()}", copy(price = MenuRange(), currency = null))
    addCondition(available, "В наличии", copy(available = false))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveMenuFilters(filter: MenuFilter, update: (MenuFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (chip in filter.chips()) {
            InputChip(true, { update(chip.cleared) }, { Text("${chip.label} ×") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MenuFilterDialog(
    rows: List<MenuPosition>,
    initial: MenuFilter,
    relevanceAvailable: Boolean,
    dismiss: () -> Unit,
    apply: (MenuFilter) -> Unit,
) {
    var filter by remember { mutableStateOf(initial) }
    val breweryChoices = rows.map { MenuChoice(it.breweryId, it.brewery) }.distinctBy(MenuChoice::id)
    val countryChoices = rows.mapNotNull { it.country?.let { name -> MenuChoice(normalized(name), name) } }.distinctBy(MenuChoice::id)
    val formatChoices = rows.flatMap { it.formatKeys() }.distinct().map { MenuChoice(it, formatTitle(it)) }
    val currencyChoices = (rows.mapNotNull { it.currency } + listOfNotNull(initial.currency)).distinct().sorted()
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(14.dp)) {
                Text("Фильтры", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(filter.query, { filter = filter.copy(query = it.take(120)) }, label = { Text("Название пива") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FilterGroup("Стили") {
                        StyleChoices(rows, filter) { filter = it }
                    }
                    ChoiceGroup("Пивоварни", breweryChoices, filter.breweries) { filter = filter.copy(breweries = it) }
                    ChoiceGroup("Страны пивоварен", countryChoices, filter.countries) { filter = filter.copy(countries = it) }
                    ChoiceGroup("Упаковка или подача", formatChoices, filter.formats) { filter = filter.copy(formats = it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (value in Tried.entries) FilterChip(filter.tried == value, { filter = filter.copy(tried = value) }, { Text(value.title) })
                    }
                    FilterGroup("Крепость, горечь, объём и рейтинг") {
                        RangeFields("ABV, %", filter.abv) { filter = filter.copy(abv = it) }
                        RangeFields("IBU", filter.ibu) { filter = filter.copy(ibu = it) }
                        RangeFields("Объём, мл", filter.volumeMl) { filter = filter.copy(volumeMl = it) }
                        NumberField("Средний рейтинг от", filter.ratingMin) { filter = filter.copy(ratingMin = it) }
                    }
                    FilterGroup("Цена") {
                        if (currencyChoices.isEmpty()) Text("В меню нет цен.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (currency in currencyChoices) {
                                FilterChip(currency == filter.currency, { filter = filter.copy(currency = currency) }, { Text(currency) })
                            }
                        }
                        RangeFields("Цена", filter.price) {
                            filter = filter.copy(price = it, currency = filter.currency ?: currencyChoices.singleOrNull())
                        }
                        if (filter.price.active && filter.currency == null) Text("Выберите валюту диапазона.")
                    }
                    FilterChip(filter.available, { filter = filter.copy(available = !filter.available) }, { Text("Только в наличии") })
                    FilterGroup("Сортировка · ${filter.sort.title}") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (sort in MenuSort.entries) {
                                if (sort == MenuSort.RELEVANCE && !relevanceAvailable && filter.sort != sort) continue
                                FilterChip(filter.sort == sort, { filter = filter.copy(sort = sort) }, { Text(sort.title) })
                            }
                        }
                    }
                    if (!filter.valid()) Text("Проверьте границы диапазонов и валюту.", color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton({ filter = MenuFilter() }) { Text("Сбросить") }
                    TextButton(dismiss) { Text("Закрыть") }
                    Button({ apply(filter) }, enabled = filter.valid()) { Text("Применить") }
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton({ expanded = !expanded }, Modifier.fillMaxWidth()) { Text(title) }
    if (expanded) content()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleChoices(rows: List<MenuPosition>, filter: MenuFilter, update: (MenuFilter) -> Unit) {
    var search by remember { mutableStateOf("") }
    OutlinedTextField(search, { search = it.take(120) }, label = { Text("Найти стиль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    for (family in MenuStyles.families) {
        val styles = MenuStyles.styles.filter { it.family == family.id && (it.matchesSearch(search) || normalized(search) in normalized(family.title)) }
        if (styles.isEmpty()) continue
        FilterChip(family.id in filter.families, {
            update(filter.copy(families = filter.families.toggle(family.id)))
        }, { Text(family.title) })
        FlowRow(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (style in styles) FilterChip(style.id in filter.styles, {
                update(filter.copy(styles = filter.styles.toggle(style.id)))
            }, { Text(style.title) })
        }
    }
    val other = rows.filter { MenuStyles.classify(it.style) == null }.mapNotNull { row ->
        val id = MenuStyles.key(row) ?: return@mapNotNull null
        MenuChoice(id, row.style ?: id)
    }.distinctBy(MenuChoice::id)
    val knownIds = MenuStyles.styles.map(MenuStyle::id).toSet()
    val choices = other.withSelected(filter.styles - knownIds).filter { normalized(search) in normalized(it.title) }
    if (choices.isNotEmpty()) Text("Другие стили каталога", style = MaterialTheme.typography.titleSmall)
    ChoiceChips(choices, filter.styles) { update(filter.copy(styles = filter.styles.toggle(it))) }
}

@Composable
private fun ChoiceGroup(title: String, choices: List<MenuChoice>, selected: Set<String>, update: (Set<String>) -> Unit) {
    FilterGroup(title) {
        val available = choices.withSelected(selected)
        if (available.isEmpty()) Text("В меню нет данных.")
        ChoiceChips(available, selected) { update(selected.toggle(it)) }
    }
}

private fun List<MenuChoice>.withSelected(selected: Set<String>): List<MenuChoice> =
    (this + (selected - map(MenuChoice::id).toSet()).sorted().map { MenuChoice(it, "Вне меню · $it") })
        .sortedBy { normalized(it.title) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(choices: List<MenuChoice>, selected: Set<String>, toggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (choice in choices) FilterChip(choice.id in selected, { toggle(choice.id) }, { Text(choice.title) })
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@Composable
private fun RangeFields(label: String, range: MenuRange, update: (MenuRange) -> Unit) {
    NumberField("$label от", range.min) { update(range.copy(min = it)) }
    NumberField("$label до", range.max) { update(range.copy(max = it)) }
}

@Composable
private fun NumberField(label: String, value: String, update: (String) -> Unit) {
    OutlinedTextField(value, { update(it.take(15)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
