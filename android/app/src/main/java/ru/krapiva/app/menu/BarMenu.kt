package ru.krapiva.app.menu

import org.json.JSONObject
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

internal data class MenuPosition(
    val positionId: String,
    val sectionId: String,
    val name: String,
    val breweryId: String,
    val brewery: String,
    val styleId: String?,
    val style: String?,
    val abv: Double?,
    val ibu: Double?,
    val averageRating: Double?,
    val country: String?,
    val packaging: String?,
    val servingFormat: String?,
    val price: BigDecimal?,
    val currency: String?,
    val volumeMl: Int?,
    val available: Boolean,
    val tried: Boolean,
    val ratedCheckins: Int? = null,
    val personalCheckins: Int? = null,
    val personalAverageRating: Double? = null,
    val description: String? = null,
    val publicAverageRating: Double? = null,
)

internal data class BarMenu(
    val updatedAt: Instant,
    val source: String,
    val sections: Map<String, String>,
    val items: List<MenuPosition>,
) {
    fun isOld(now: Instant): Boolean = Duration.between(updatedAt, now) >= Duration.ofHours(24)

    companion object {
        fun read(response: JSONObject): BarMenu? = response.optJSONObject("menu")?.let { menu ->
            BarMenu(
                Instant.parse(menu.getString("updatedAt")),
                menu.getString("source"),
                menu.getJSONArray("sections").objects().associate { it.getString("id") to it.getString("name") },
                menu.getJSONArray("items").objects().map { row ->
                    MenuPosition(
                        row.getString("positionId"), row.getString("sectionId"), row.getString("name"),
                        row.getString("breweryId"), row.getString("breweryName"), row.text("styleId"), row.text("styleName"),
                        row.text("abv")?.toDoubleOrNull(), row.text("ibu")?.toDoubleOrNull(), row.text("averageRating")?.toDoubleOrNull(),
                        row.text("country"), row.text("packaging"), row.text("servingFormat"),
                        row.text("price")?.toBigDecimalOrNull(), row.text("currency"),
                        row.text("volumeMl")?.toIntOrNull() ?: row.text("catalogVolumeMl")?.toIntOrNull(),
                        row.getBoolean("available"), row.getBoolean("tried"),
                        row.text("ratedCheckins")?.toIntOrNull(), row.text("personalCheckins")?.toIntOrNull(),
                        row.text("personalAverageRating")?.toDoubleOrNull(), row.text("description")?.takeIf { it.isNotBlank() },
                        row.text("publicAverageRating")?.toDoubleOrNull(),
                    )
                },
            )
        }
    }
}

internal fun MenuPosition.checkinSeed(): JSONObject = JSONObject()
    .put("canonicalId", positionId)
    .put("id", positionId)
    .put("name", name)
    .put("breweryName", brewery)
    .put("styleName", style)
