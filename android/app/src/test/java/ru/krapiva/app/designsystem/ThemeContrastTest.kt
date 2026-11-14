package ru.krapiva.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float =
        (max(a.luminance(), b.luminance()) + .05f) / (min(a.luminance(), b.luminance()) + .05f)

    @Test fun bothPalettesKeepBodyLabelsAndActionsReadable() {
        assertEquals(Color(0xFF1B211E), Dark.background)
        assertEquals(Color(0xFFF7F5F0), Light.background)
        for (scheme in listOf(Dark, Light)) {
            for (background in listOf(scheme.background, scheme.surface, scheme.surfaceVariant)) {
                assertTrue(contrast(scheme.onSurface, background) >= 4.5f)
                assertTrue(contrast(scheme.onSurfaceVariant, background) >= 4.5f)
            }
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
        val pattern = Color(0xFF9CB79E).copy(alpha = .085f).compositeOver(Dark.background)
        assertTrue(contrast(Dark.onSurfaceVariant, pattern) >= 4.5f)
    }
}
