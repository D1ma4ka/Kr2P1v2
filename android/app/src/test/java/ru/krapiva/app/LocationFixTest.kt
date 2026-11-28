package ru.krapiva.app

import org.junit.Assert.*
import org.junit.Test
import ru.krapiva.app.places.usableFix

class LocationFixTest {
    @Test fun acceptsFreshPreciseAndApproximateFixes() {
        for (accuracy in listOf(5f, 3000f)) {
            assertTrue(usableFix(59.9343, 30.3351, 30_000_000_000L, true, accuracy, false, false))
        }
    }

    @Test fun rejectsStaleInvalidAndPhysicalMockFixesWithoutGeographicBlacklist() {
        assertFalse(usableFix(55.75, 37.61, 30_000_000_001L, true, 5f, false, false))
        assertFalse(usableFix(Double.NaN, 37.61, 0, true, 5f, false, false))
        assertFalse(usableFix(55.75, 200.0, 0, true, 5f, false, false))
        assertFalse(usableFix(55.75, 37.61, 0, false, 0f, false, false))
        assertFalse(usableFix(47.6062, -122.3321, 0, true, 5f, true, false))
        assertTrue(usableFix(47.6062, -122.3321, 0, true, 5f, false, false))
    }
}
