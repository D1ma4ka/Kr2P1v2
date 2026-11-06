package ru.krapiva.app.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.TestEvidence
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.ParcelFileDescriptor
import org.junit.Assume.assumeTrue

class BeerHeroTest {
    @get:Rule val compose = createComposeRule()
    @Test fun boundedLeftRightAndStaticAreVisible() {
        compose.setContent { KraPivaTheme { Column {
            Text("KraPiva — liquid states")
            BeerHero(previewAngle = -8f)
            BeerHero(previewAngle = 8f)
            BeerHero(forceReducedMotion = true)
        } } }
        compose.onAllNodesWithContentDescription("Фирменный бокал KraPiva с пеной и хмелем").assertCountEquals(3)
        TestEvidence.screenshot("p3-liquid-states.png")
    }
    @Test fun noSensorLoopMovesAndReducedMotionStaysStill() {
        compose.setContent { KraPivaTheme { Column {
            BeerHero(Modifier.testTag("fallback"), forceNoSensor = true)
            BeerHero(Modifier.testTag("static"), forceReducedMotion = true)
        } } }
        val moving = compose.onNodeWithTag("fallback").captureToImage().asAndroidBitmap()
        val still = compose.onNodeWithTag("static").captureToImage().asAndroidBitmap()
        android.os.SystemClock.sleep(700)
        assertFalse(moving.sameAs(compose.onNodeWithTag("fallback").captureToImage().asAndroidBitmap()))
        assertTrue(still.sameAs(compose.onNodeWithTag("static").captureToImage().asAndroidBitmap()))
    }
    @Test fun sensorSubscriptionStopsOnPauseAndDisposal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = instrumentation.targetContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        assumeTrue(manager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null || manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
        lateinit var owner: LifecycleOwner
        lateinit var registry: LifecycleRegistry
        instrumentation.runOnMainSync {
            owner = object : LifecycleOwner { override val lifecycle get() = registry }
            registry = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
        }
        val visible = mutableStateOf(true)
        fun subscriptions(): Int = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys sensorservice")).use {
            // Connection objects/history can outlive unregistration. Count active
            // sensor handles in this app's connection, not occurrences of its name.
            val connections = it.readBytes().toString(Charsets.UTF_8)
                .substringBefore("Previous Registrations:").split("Connection Number:").drop(1)
            connections.filter { connection -> connection.contains(instrumentation.targetContext.packageName) }
                .sumOf { connection -> Regex("0x[0-9a-fA-F]+\\s*\\|\\s*first flush pending").findAll(connection).count() }
        }
        fun expectSubscriptions(expected: Int, phase: String) {
            var actual = -1
            try { compose.waitUntil(10_000) { actual = subscriptions(); actual == expected } }
            catch (error: Exception) { throw AssertionError("Sensor subscriptions during $phase: expected=$expected, actual=$actual", error) }
        }
        compose.setContent { KraPivaTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { if (visible.value) BeerHero() }
        } }
        expectSubscriptions(1, "resume")
        compose.runOnIdle { registry.currentState = Lifecycle.State.CREATED }
        expectSubscriptions(0, "pause")
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        expectSubscriptions(1, "second resume without duplicate listeners")
        compose.runOnIdle { visible.value = false }
        expectSubscriptions(0, "disposal")
    }
}
