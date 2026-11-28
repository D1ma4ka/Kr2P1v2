package ru.krapiva.app.places

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krapiva.app.MainActivity
import kotlin.math.abs

/** Foreground GPS integration. The host supplies the expected adb emu geo fix on an isolated AVD. */
class DeviceFixTest {
    @Test fun readsConfiguredCityFromSystemProvider() {
        val args = InstrumentationRegistry.getArguments()
        val latitude = requireNotNull(args.getString("latitude")).toDouble()
        val longitude = requireNotNull(args.getString("longitude")).toDouble()
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertTrue(hasLocationPermission(context))
            runBlocking {
                withTimeout(60_000) {
                    do {
                        val point = currentLocation(context)
                        if (point != null && abs(point.latitude - latitude) < 0.05 &&
                            abs(point.longitude - longitude) < 0.05) break
                        delay(500)
                    } while (true)
                }
            }
        }
    }
}
