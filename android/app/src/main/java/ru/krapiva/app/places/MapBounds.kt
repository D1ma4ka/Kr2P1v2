package ru.krapiva.app.places

/** Longitude wrapping must retain a crossing of the antimeridian. */
data class MapBounds(val west: Double,val south: Double,val east: Double,val north: Double) {
    fun query() = "$west,$south,$east,$north"
    companion object {
        fun viewport(west: Double,south: Double,east: Double,north: Double): MapBounds {
            require(listOf(west,south,east,north).all(Double::isFinite) && south < north && west != east)
            fun wrap(n: Double) = ((n + 180) % 360 + 360) % 360 - 180
            require(south.coerceAtLeast(-90.0)<north.coerceAtMost(90.0))
            val world = kotlin.math.abs(east-west) >= 360
            return MapBounds(if(world)-180.0 else wrap(west),south.coerceAtLeast(-90.0),if(world)180.0 else wrap(east),north.coerceAtMost(90.0))
        }
    }
}
