package ru.krapiva.app.places

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class Coordinates(val latitude: Double, val longitude: Double)
data class DiscoveryCity(val name: String, val center: Coordinates?)

internal fun venueTypeLabel(type: String): String = when (type) {
    "bar" -> "Бар"
    "beer_shop" -> "Пивной магазин"
    "brewery" -> "Пивоварня"
    else -> "Заведение"
}

internal val nearbyRadii = listOf(1, 3, 5, 10, 25, 50)

internal class NearbyPreference(context: Context) {
    private val settings = context.getSharedPreferences("nearby", Context.MODE_PRIVATE)
    var radius by mutableIntStateOf(settings.getInt("radiusKm", 5).takeIf { it in nearbyRadii } ?: 5)
        private set

    fun select(value: Int) {
        require(value in nearbyRadii)
        radius = value
        settings.edit().putInt("radiusKm", value).apply()
    }
}

internal fun Coordinates.request(radius: Int): JSONObject =
    JSONObject().put("latitude", latitude).put("longitude", longitude).put("radiusKm", radius)
