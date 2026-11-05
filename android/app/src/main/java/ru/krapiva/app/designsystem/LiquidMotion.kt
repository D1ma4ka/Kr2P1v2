package ru.krapiva.app.designsystem

import kotlin.math.atan2
import kotlin.math.abs

/** Gravity input is transient, local and never persisted. Rotation values: 0, 1, 2, 3. */
class LiquidMotion {
    var angle = 0f
        private set
    fun target(x: Float, y: Float, rotation: Int): Float {
        if (!x.isFinite() || !y.isFinite() || abs(x) + abs(y) < 0.01f) return 0f
        val horizontal = when (rotation) { 1 -> -y; 2 -> -x; 3 -> y; else -> x }
        val vertical = when (rotation) { 1, 3 -> x; else -> y }
        return Math.toDegrees(atan2(-horizontal.toDouble(), abs(vertical).toDouble().coerceAtLeast(0.1))).toFloat().coerceIn(-8f, 8f)
    }
    fun step(target: Float): Float {
        val safe = if (target.isFinite()) target.coerceIn(-8f, 8f) else 0f
        angle += (safe - angle) * 0.12f
        return angle
    }
}
