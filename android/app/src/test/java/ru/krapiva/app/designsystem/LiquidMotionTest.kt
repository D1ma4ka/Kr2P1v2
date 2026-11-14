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
    @Test fun inertiaSurvivesDirectionChangeAndOscillationSettles() {
        val motion = LiquidMotion()
        motion.step(4f)
        val before = motion.step(4f)
        assertTrue("Momentum should survive a direction change", motion.step(-4f) > before)
        repeat(100) { motion.step(-4f) }
        assertEquals(-4f, motion.angle, .001f)
        repeat(600) { step ->
            val value = motion.step(if (step % 30 < 15) 100f else -100f)
            assertTrue(value.isFinite() && value in -8f..8f)
        }
    }
    @Test fun resumeStartsAtTheFrozenSurfaceInsteadOfJumpingToZero() {
        val resumed = LiquidMotion(8f)
        assertEquals(8f, resumed.step(8f), .001f)
        assertTrue(resumed.step(0f) in 7f..8f)
        assertEquals(8f, LiquidMotion(100f).angle, .001f)
        assertEquals(0f, LiquidMotion(Float.NaN).angle, .001f)
    }
}
