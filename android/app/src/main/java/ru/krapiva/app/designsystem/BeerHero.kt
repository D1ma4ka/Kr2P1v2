package ru.krapiva.app.designsystem

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun BeerHero(modifier: Modifier = Modifier, previewAngle: Float? = null, forceReducedMotion: Boolean = false, forceNoSensor: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    var available by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0f) }
    val angle = remember { mutableFloatStateOf(0f) }
    val hop = remember { PathParser().parsePathString("M54,45C37,47 35,61 42,75Q47,84 54,88Q61,84 66,75C73,61 71,47 54,45Z").toPath() }
    val scales = remember { PathParser().parsePathString("M54,45V40 M54,48Q48,59 40,60 M54,48Q60,59 68,60 M40,59Q43,70 54,72Q65,70 68,59 M44,70Q47,80 54,85Q61,80 64,70 M54,62Q49,67 47,74 M54,62Q59,67 61,74").toPath() }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        owner.lifecycle.addObserver(observer)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settings)
        onDispose { owner.lifecycle.removeObserver(observer); context.contentResolver.unregisterContentObserver(settings) }
    }
    val static = reduced || forceReducedMotion
    DisposableEffect(manager, active, static, previewAngle, forceNoSensor) {
        val model = LiquidMotion()
        val sensor = if (forceNoSensor) null else manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        var filteredX = 0f; var filteredY = 9.81f
        val listener = object : SensorEventListener {
            @Suppress("DEPRECATION")
            override fun onSensorChanged(event: SensorEvent) {
                if (event.timestamp - last < 33_000_000L) return
                last = event.timestamp
                filteredX += (event.values[0] - filteredX) * 0.2f
                filteredY += (event.values[1] - filteredY) * 0.2f
                val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                target = model.target(filteredX, filteredY, rotation)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        available = active && !static && previewAngle == null && sensor != null && manager.registerListener(listener, sensor, 33_333)
        onDispose { manager.unregisterListener(listener) }
    }
    LaunchedEffect(active, static, available, previewAngle) {
        if (static || !active) { angle.floatValue = 0f; return@LaunchedEffect }
        if (previewAngle != null) { angle.floatValue = previewAngle.coerceIn(-8f, 8f); return@LaunchedEffect }
        val model = LiquidMotion()
        var frame = 0
        while (true) {
            val wanted = if (available) target else sin(frame++ * 0.035f) * 2f
            angle.floatValue = model.step(wanted)
            delay(33)
        }
    }
    Canvas(modifier.size(112.dp).semantics { contentDescription = "Фирменный бокал KraPiva с пеной и хмелем" }) {
        val w = size.width; val h = size.height
        val glass = Path().apply {
            moveTo(w * .23f, h * .2f); lineTo(w * .77f, h * .2f)
            lineTo(w * .69f, h * .87f); quadraticTo(w * .5f, h * .93f, w * .31f, h * .87f); close()
        }
        val delta = tan(Math.toRadians(angle.floatValue.toDouble())).toFloat() * w
        val liquid = Path().apply {
            moveTo(0f, h * .4f - delta / 2); cubicTo(w * .3f, h * .37f, w * .7f, h * .43f, w, h * .4f + delta / 2)
            lineTo(w, h); lineTo(0f, h); close()
        }
        clipPath(glass) {
            drawPath(liquid, KraPivaColors.Amber)
            val foam = Path().apply { moveTo(0f, h * .4f - delta / 2); cubicTo(w * .3f, h * .37f, w * .7f, h * .43f, w, h * .4f + delta / 2) }
            drawPath(foam, KraPivaColors.Cream, style = Stroke(h * .075f))
        }
        drawPath(glass, KraPivaColors.Cream, style = Stroke(w * .025f))
        // Original hop cone with overlapping scales, shared with static brand artwork.
        withTransform({ scale(w / 108f, h / 108f, pivot = Offset.Zero) }) {
            drawPath(hop, KraPivaColors.Hop)
            drawPath(scales, KraPivaColors.Ink, style = Stroke(2f))
        }
        for (side in listOf(0, 1)) for (vertical in listOf(0, 1)) {
            val x = if (side == 0) w * .06f else w * .94f
            val y = if (vertical == 0) h * .08f else h * .96f
            val dx = if (side == 0) w * .13f else -w * .13f
            val dy = if (vertical == 0) h * .12f else -h * .12f
            drawLine(KraPivaColors.Cream, Offset(x, y), Offset(x + dx, y), w * .025f)
            drawLine(KraPivaColors.Cream, Offset(x, y), Offset(x, y + dy), w * .025f)
        }
    }
}
