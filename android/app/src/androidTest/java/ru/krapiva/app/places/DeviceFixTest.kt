package ru.krapiva.app.places

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krapiva.app.MainActivity
import kotlin.math.abs

/** Foreground GPS integration. The host supplies the expected adb emu geo fix on an isolated AVD. */
class DeviceFixTest {
    @Suppress("MissingPermission")
    @Test fun readsConfiguredCityFromSystemProvider() {
        val args = InstrumentationRegistry.getArguments()
        val latitude = requireNotNull(args.getString("latitude")).toDouble()
        val longitude = requireNotNull(args.getString("longitude")).toDouble()
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager = context.getSystemService(LocationManager::class.java)
            assertEquals("The GPS scenario needs precise foreground permission", PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue("System GPS provider must be enabled", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            val matchingFix = runBlocking {
                withTimeoutOrNull(60_000) {
                    var matches = false
                    while (!matches) {
                        val point = withTimeoutOrNull(5_000) { currentLocation(context) }
                        matches = point != null && abs(point.latitude - latitude) < 0.05 &&
                            abs(point.longitude - longitude) < 0.05
                        if (!matches) delay(500)
                    }
                    true
                } ?: false
            }
            if (!matchingFix) {
                val systemFix = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val systemStatus = systemFix?.let {
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
                    "accuracy=${it.hasAccuracy()}, ageMs=$ageMs, expectedCoordinates=" +
                        (abs(it.latitude - latitude) < 0.05 && abs(it.longitude - longitude) < 0.05)
                } ?: "none"
                throw AssertionError("No matching location from the app within 60s. System GPS fix: $systemStatus")
            }
        }
    }
}
