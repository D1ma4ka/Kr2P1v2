package ru.krapiva.app.designsystem

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "p3-liquid-states.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
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
        fun subscribed(): Boolean = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys sensorservice")).use {
            it.readBytes().toString(Charsets.UTF_8).substringAfter("open event connections")
                .substringBefore("Previous Registrations:").contains(instrumentation.targetContext.packageName)
        }
        compose.setContent { KraPivaTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { if (visible.value) BeerHero() }
        } }
        compose.waitUntil(10_000) { subscribed() }
        compose.runOnIdle { registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(10_000) { !subscribed() }
        compose.runOnIdle { registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { subscribed() }
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(10_000) { !subscribed() }
    }
}
