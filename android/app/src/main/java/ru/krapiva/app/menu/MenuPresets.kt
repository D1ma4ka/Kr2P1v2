package ru.krapiva.app.menu

import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.diary.objects
import java.time.Instant
import java.util.UUID

internal data class ThemePreset(val id: String, val name: String, val description: String, val filter: MenuFilter)

internal val themePresets = listOf(
    ThemePreset("quiet-saturday", "Тихая суббота", "Belgian Blonde, Dubbel, Tripel и другие бельгийские эли.",
        MenuFilter(families = setOf(MenuStyles.BELGIAN))),
    ThemePreset("hop-route", "Хмелевой маршрут", "IPA, APA и родственные эли, сначала с высоким рейтингом.",
        MenuFilter(families = setOf(MenuStyles.HOPPY), sort = MenuSort.RATING)),
    ThemePreset("dark-side", "Тёмная сторона", "Стауты и портеры. Крепость можно выбрать в фильтрах.",
        MenuFilter(families = setOf(MenuStyles.DARK))),
    ThemePreset("sour-expedition", "Кислая экспедиция", "Gose, Lambic, Berliner Weisse и другие кислые стили.",
        MenuFilter(families = setOf(MenuStyles.SOUR))),
    ThemePreset("new-hunt", "Охота за новым", "Непробованные позиции всех стилей из этого меню.",
        MenuFilter(tried = Tried.NO, sort = MenuSort.RELEVANCE)),
)

internal data class SavedMenuFilter(
    val id: String,
    val name: String,
    val filter: MenuFilter,
    val updatedAt: Instant,
    val version: Int = 1,
    val deleted: Boolean = false,
)

internal data class MenuPresetBook(
    val records: List<SavedMenuFilter> = emptyList(),
    val defaultId: String? = null,
    val recentIds: List<String> = emptyList(),
) {
    val active get() = records.filterNot { it.deleted }

    fun filter(id: String): MenuFilter? =
        active.firstOrNull { it.id == id }?.filter ?: themePresets.firstOrNull { it.id == id }?.filter

    fun title(id: String): String? =
        active.firstOrNull { it.id == id }?.name ?: themePresets.firstOrNull { it.id == id }?.name

    fun save(id: String, name: String, filter: MenuFilter, now: Instant): MenuPresetBook {
        val title = name.trim()
        require(title.length in 1..80 && filter.valid())
        val old = active.firstOrNull { it.id == id }
        require(old != null || records.none { it.id == id })
        val updated = SavedMenuFilter(id, title, filter, now, (old?.version ?: 0) + 1)
        return copy(records = listOf(updated) + records.filterNot { it.id == id })
    }

    fun rename(id: String, name: String, now: Instant): MenuPresetBook {
        val preset = active.first { it.id == id }
        return save(id, name, preset.filter, now)
    }

    fun update(id: String, filter: MenuFilter, now: Instant): MenuPresetBook {
        val preset = active.first { it.id == id }
        return save(id, preset.name, filter, now)
    }

    fun duplicate(id: String, newId: String, name: String, now: Instant): MenuPresetBook =
        save(newId, name, active.first { it.id == id }.filter, now)

    fun delete(id: String, now: Instant): MenuPresetBook = copy(
        records = records.map { if (it.id == id && !it.deleted) it.copy(deleted = true, version = it.version + 1, updatedAt = now) else it },
        defaultId = defaultId?.takeUnless { it == id },
        recentIds = recentIds - id,
    )

    fun selectDefault(id: String): MenuPresetBook {
        require(active.any { it.id == id })
        return copy(defaultId = if (defaultId == id) null else id)
    }

    fun use(id: String): MenuPresetBook {
        require(filter(id) != null)
        return copy(recentIds = (listOf(id) + (recentIds - id)).take(8))
    }
}

internal class MenuPresetRepository(private val api: AccountApi, private val owner: String) {
    private val key = "smart-bar-filters-v1"

    suspend fun read(): MenuPresetBook = decode(api.localDocument(key, expectedOwner = owner))

    suspend fun edit(change: (MenuPresetBook) -> MenuPresetBook): MenuPresetBook {
        val stored = api.editLocalDocument(key, expectedOwner = owner) { document ->
            val book = change(decode(document))
            document.put("schemaVersion", 1)
            document.put("ownerUserId", owner)
            document.put("defaultId", book.defaultId ?: JSONObject.NULL)
            document.put("recentIds", JSONArray(book.recentIds))
            document.put("records", JSONArray(book.records.map { record ->
                JSONObject().put("id", record.id).put("name", record.name).put("filter", record.filter.encode())
                    .put("updatedAt", record.updatedAt.toString()).put("version", record.version).put("deleted", record.deleted)
            }))
        }
        return decode(stored)
    }

    private fun decode(document: JSONObject): MenuPresetBook {
        if (document.length() == 0) return MenuPresetBook()
        require(document.getInt("schemaVersion") == 1 && document.getString("ownerUserId") == owner)
        val records = document.getJSONArray("records").objects().map { row ->
            SavedMenuFilter(
                row.getString("id"), row.getString("name"), MenuFilter.decode(row.getJSONObject("filter")),
                Instant.parse(row.getString("updatedAt")), row.getInt("version"), row.getBoolean("deleted"),
            )
        }
        val recent = document.getJSONArray("recentIds")
        return MenuPresetBook(records, if (document.isNull("defaultId")) null else document.getString("defaultId"),
            (0 until recent.length()).map(recent::getString))
    }
}

internal fun newMenuFilterId(): String = UUID.randomUUID().toString()
