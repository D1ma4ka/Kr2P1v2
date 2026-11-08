package ru.krapiva.app.designsystem

import org.junit.Assert.*
import org.junit.Test

class LiquidMotionTest {
    @Test fun clampAndOrientation() {
        val motion = LiquidMotion()
        assertEquals(-8f, motion.target(9f, 1f, 0), .001f)
        assertEquals(8f, motion.target(9f, 1f, 2), .001f)
        assertEquals(8f, motion.target(1f, 9f, 1), .001f)
        assertEquals(-8f, motion.target(1f, 9f, 3), .001f)
        assertEquals(0f, motion.target(Float.NaN, 9f, 0), .001f)
    }
    @Test fun filterIsSmoothBoundedAndSettles() {
        val motion = LiquidMotion()
        val first = motion.step(100f)
        assertTrue(first > 0 && first < 1f)
        repeat(100) { assertTrue(motion.step(100f) <= 8f) }
        assertEquals(8f, motion.angle, .001f)
        repeat(100) { motion.step(0f) }
        assertEquals(0f, motion.angle, .001f)
        assertTrue(motion.step(Float.NaN).isFinite())
    }
}
