package ru.krapiva.app.designsystem

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.sin

internal class LiquidAnimation(val angle: State<Float>, val phase: State<Float>, val fill: State<Float>)

/** One local sensor subscription, at most 30 updates/s, only while this screen is resumed. */
@Composable
internal fun rememberLiquidAnimation(
    still: Boolean = false, previewAngle: Float? = null, forceNoSensor: Boolean = false,
    reveal: Boolean = false, continuous: Boolean = true,
    projectedSurface: Boolean = false,
): LiquidAnimation {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    var available by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0f) }
    val angle = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    val fill = remember { mutableFloatStateOf(if (reveal) 0f else 1f) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        owner.lifecycle.addObserver(observer)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settings)
        onDispose { owner.lifecycle.removeObserver(observer); context.contentResolver.unregisterContentObserver(settings) }
    }
    val static = still || reduced
    DisposableEffect(manager, active, static, previewAngle, forceNoSensor, continuous, projectedSurface) {
        val sensor = if (forceNoSensor || !continuous) null else {
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        val model = LiquidMotion()
        var last = 0L
        var filteredX = 0f; var filteredY = 9.81f
        val listener = object : SensorEventListener {
            @Suppress("DEPRECATION")
            override fun onSensorChanged(event: SensorEvent) {
                if (event.timestamp - last < 33_000_000L) return
                last = event.timestamp
                filteredX += (event.values[0] - filteredX) * .2f
                filteredY += (event.values[1] - filteredY) * .2f
                val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                target = model.target(filteredX, filteredY, rotation, projectedSurface)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        target = 0f
        available = active && !static && previewAngle == null && sensor != null && manager.registerListener(listener, sensor, 33_333)
        onDispose { manager.unregisterListener(listener) }
    }
    LaunchedEffect(active, static, available, previewAngle, continuous) {
        if (static) { angle.floatValue = 0f; phase.floatValue = 0f; fill.floatValue = 1f; return@LaunchedEffect }
        if (!active) return@LaunchedEffect
        if (previewAngle != null) {
            angle.floatValue = previewAngle.coerceIn(-8f, 8f); fill.floatValue = 1f
            return@LaunchedEffect
        }
        // Continue from the frozen surface when returning from the background.
        val model = LiquidMotion(angle.floatValue)
        while (continuous || fill.floatValue < 1f) {
            phase.floatValue = (phase.floatValue + .033f) % 120f
            fill.floatValue = (fill.floatValue + .033f / 1.8f).coerceAtMost(1f)
            angle.floatValue = model.step(if (available) target else sin(phase.floatValue * .8f) * 2f)
            delay(33)
        }
    }
    return remember(angle, phase, fill) { LiquidAnimation(angle, phase, fill) }
}
