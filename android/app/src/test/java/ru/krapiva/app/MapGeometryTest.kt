package ru.krapiva.app

import org.junit.Assert.*
import org.junit.Test
import ru.krapiva.app.places.MapBounds

class MapGeometryTest {
    @Test fun dateLineAndWorldBounds() {
        assertEquals("170.0,-20.0,-170.0,20.0",MapBounds.viewport(170.0,-20.0,190.0,20.0).query())
        assertEquals("-180.0,-85.0,180.0,85.0",MapBounds.viewport(-220.0,-85.0,220.0,85.0).query())
        assertEquals("-180.0,-90.0,180.0,90.0",MapBounds.viewport(-180.0,-92.0,180.0,92.0).query())
    }
    @Test fun rejectsUnusableViewport() {
        for(v in listOf(doubleArrayOf(0.0,1.0,0.0,2.0),doubleArrayOf(0.0,2.0,1.0,1.0),doubleArrayOf(Double.NaN,0.0,1.0,1.0))) {
            assertThrows(IllegalArgumentException::class.java) { MapBounds.viewport(v[0],v[1],v[2],v[3]) }
        }
    }
}
