package ru.krapiva.app.designsystem

import kotlin.math.atan2
import kotlin.math.abs

/** Gravity input is transient, local and never persisted. Rotation values: 0, 1, 2, 3. */
class LiquidMotion(initialAngle: Float = 0f) {
    var angle = (if (initialAngle.isFinite()) initialAngle else 0f).coerceIn(-8f, 8f)
        private set
    private var velocity = 0f
    fun target(x: Float, y: Float, rotation: Int): Float {
        if (!x.isFinite() || !y.isFinite() || abs(x) + abs(y) < 0.01f) return 0f
        val horizontal = when (rotation) { 1 -> -y; 2 -> -x; 3 -> y; else -> x }
        val vertical = when (rotation) { 1, 3 -> x; else -> y }
        return Math.toDegrees(atan2(-horizontal.toDouble(), abs(vertical).toDouble().coerceAtLeast(0.1))).toFloat().coerceIn(-8f, 8f)
    }
    fun step(target: Float): Float {
        val safe = if (target.isFinite()) target.coerceIn(-8f, 8f) else 0f
        // Damped spring at the shared 30 Hz cadence: inertia without abrupt jumps.
        velocity = (velocity + (safe - angle) * 0.10f) * 0.78f
        val next = angle + velocity
        angle = next.coerceIn(-8f, 8f)
        if (next != angle) velocity = 0f
        return angle
    }
}
