package ru.krapiva.app.pets

import android.animation.ValueAnimator
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun JSONObject.entries() = keys().asSequence().map { it to getJSONObject(it) }.toList()
private fun JSONObject.point(key: String): Pair<Float,Float> = getJSONArray(key).let {
    it.getDouble(0).toFloat() to it.getDouble(1).toFloat()
}
private fun JSONObject.number(key: String, fallback: Float = 0f) = optDouble(key, fallback.toDouble()).toFloat()
internal data class PetGesture(
    val clip: String,
    val priority: Int = 1,
    val sequence: Long = 0,
    val followUp: String? = null,
)
internal data class PetFit(val slot: String, val bitmap: Bitmap, val render: JSONObject)

internal class PetRig private constructor(
    data: JSONObject,
    images: Map<String, Bitmap>,
    motion: JSONObject,
    private val full: Boolean,
) {
    private data class Sprite(val bitmap: Bitmap, val bounds: RectF)
    private data class Bone(val name: String, val parent: Bone?, val x: Float, val y: Float, val matrix: Matrix = Matrix())
    private data class Key(
        val time: Float, val x: Float, val y: Float, val rotation: Float, val scaleX: Float, val scaleY: Float,
    )
    private data class Variant(val time: Float, val name: String)
    private data class Clip(
        val duration: Float, val transitionDuration: Float, val loops: Boolean, val holds: Boolean,
        val tracks: Map<String, List<Key>>, val variants: Map<String, List<Variant>>,
    )
    private data class Layer(
        val name: String, val bone: Bone, val depth: Int, val sprite: Sprite, val variants: Map<String, Sprite>,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bones: Map<String, Bone> = buildMap {
        val definitions = data.getJSONObject("bones")
        fun resolve(name: String): Bone = get(name) ?: run {
            val bone = definitions.getJSONObject(name)
            val parent = bone.optString("parent").takeIf(String::isNotEmpty)?.let(::resolve)
            val (x, y) = bone.point("pivot")
            Bone(name, parent, x, y).also { put(name, it) }
        }
        definitions.keys().forEach { resolve(it) }
    }
    private val orderedBones = bones.values.toList()
    private val clips = (motion.entries() + data.getJSONObject("clips").entries()).associate { (name, clip) ->
        val tracks = clip.optJSONObject("tracks")?.let { tracks ->
            tracks.keys().asSequence().associateWith { bone ->
                tracks.getJSONArray(bone).objects().map { key ->
                    Key(key.number("time"), key.number("x"), key.number("y"), key.number("rotation"),
                        key.number("scaleX", 1f), key.number("scaleY", 1f))
                }
            }
        }.orEmpty()
        val variants = clip.optJSONObject("variants")?.let { variants ->
            variants.keys().asSequence().associateWith { layer ->
                variants.getJSONArray(layer).objects().map { Variant(it.number("time"), it.getString("value")) }
            }
        }.orEmpty()
        val duration = clip.number("duration")
        name to Clip(duration, clip.number("holdFrom", duration), clip.optBoolean("loop"), clip.optBoolean("hold"), tracks, variants)
    }

    private fun sprite(data: JSONObject, images: Map<String, Bitmap>): Sprite {
        val (x, y) = data.point("offset")
        val (w, h) = data.point("size")
        return Sprite(images.getValue(data.getString("src")), RectF(x, y, x + w, y + h))
    }

    private val layers = if (full) data.getJSONObject("layers").entries().map { (name, layer) ->
        val variants = layer.optJSONObject("variants")?.entries()?.associate { (key, value) -> key to sprite(value, images) }.orEmpty()
        Layer(name, bones.getValue(layer.getString("bone")), layer.getInt("depth"), sprite(layer, images), variants)
    } else {
        listOf(Layer("neutral", bones.getValue("root"), Int.MIN_VALUE, sprite(data.getJSONObject("neutral"), images), emptyMap()))
    }
    private val anchors = data.getJSONObject("anchors")
    val width = data.point("canvas").first
    val height = data.point("canvas").second
    private val root = bones.getValue("root")
    private val footY = with(data.getJSONObject("neutral")) { point("offset").second + point("size").second }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawGrounded(canvas: AndroidCanvas, width: Float, height: Float, draw: () -> Unit) {
        val scale = minOf(width, height) * .50f / this.height
        canvas.save()
        canvas.translate(width * .515f - root.x * scale, height * .89f - footY * scale)
        canvas.scale(scale, scale)
        draw()
        canvas.restore()
    }

    fun holds(name: String): Boolean = clips[name]?.holds == true
    fun duration(name: String): Float = clips[name]?.duration ?: 600f
    fun transitionDuration(name: String): Float = clips[name]?.transitionDuration ?: 600f

    private fun updateBone(bone: Bone, keys: List<Key>?, time: Float, direction: Float) {
        val transform = bone.matrix
        transform.reset()
        if (!keys.isNullOrEmpty()) {
            val next = keys.indexOfFirst { it.time > time }
            val left = when {
                next < 0 -> keys.last()
                next == 0 -> keys.first()
                else -> keys[next - 1]
            }
            val right = if (next > 0) keys[next] else left
            val fraction = if (left === right) 0f else (time - left.time) / (right.time - left.time)
            fun interpolate(a: Float, b: Float) = a + (b - a) * fraction
            transform.postTranslate(-bone.x, -bone.y)
            transform.postScale(interpolate(left.scaleX, right.scaleX), interpolate(left.scaleY, right.scaleY))
            transform.postRotate(interpolate(left.rotation, right.rotation))
            transform.postTranslate(bone.x + direction * interpolate(left.x, right.x), bone.y + interpolate(left.y, right.y))
        }
        bone.parent?.let { transform.postConcat(it.matrix) }
    }

    fun renderer(fits: List<PetFit>, room: Boolean = false): (AndroidCanvas, String, Float) -> Unit {
        val parts = mutableListOf<Pair<Int, (AndroidCanvas, Clip?, Float) -> Unit>>()
        for (layer in layers) {
            parts += layer.depth to { canvas, clip, time ->
                val keys = clip?.variants?.get(layer.name)
                val variant = keys?.lastOrNull { it.time <= time } ?: keys?.firstOrNull()
                val sprite = layer.variants[variant?.name] ?: layer.sprite
                canvas.save()
                canvas.concat(layer.bone.matrix)
                canvas.drawBitmap(sprite.bitmap, null, sprite.bounds, paint)
                canvas.restore()
            }
        }
        for (fit in fits) {
            val anchor = anchors.optJSONObject(fit.slot) ?: continue
            val bone = bones.getValue(anchor.getString("bone"))
            val (x, y) = anchor.point("position")
            val a = x + fit.render.number("dx")
            val b = y + fit.render.number("dy")
            val rotation = anchor.number("rotation") + fit.render.number("rotation")
            val scale = anchor.number("scale", 1f) * fit.render.number("scale", 1f)
            val scaleX = scale * anchor.number("scaleX", 1f)
            val scaleY = scale * anchor.number("scaleY", 1f)
            val w = fit.render.number("displayWidth", 300f)
            val h = w * fit.bitmap.height / fit.bitmap.width
            val ox = w * fit.render.number("originX", .5f)
            val oy = h * fit.render.number("originY", .5f)
            val bounds = RectF(-ox, -oy, w - ox, h - oy)
            val placement = Matrix().apply {
                setScale(scaleX, scaleY)
                postRotate(rotation)
                postTranslate(a, b)
            }
            parts += anchor.getInt("depth") to { canvas, _, _ ->
                canvas.save()
                canvas.concat(bone.matrix)
                canvas.concat(placement)
                canvas.drawBitmap(fit.bitmap, null, bounds, paint)
                canvas.restore()
            }
        }
        val ordered = parts.sortedBy { it.first }.map { it.second }
        val shadowPoint = FloatArray(2)
        val shadowBounds = RectF()
        return { canvas, name, elapsed ->
            val clip = if (full) clips[name] else null
            val time = if (clip == null) 0f else if (clip.loops) elapsed % clip.duration else elapsed.coerceAtMost(clip.duration)
            val towardTable = room && (name == "sit" || name == "eat")
            for (bone in orderedBones) {
                val direction = if (towardTable && bone === root) -1f else 1f
                updateBone(bone, clip?.tracks?.get(bone.name), time, direction)
            }
            if (room) {
                shadowPoint[0] = root.x
                shadowPoint[1] = footY
                root.matrix.mapPoints(shadowPoint)
                val x = shadowPoint[0]
                val y = footY + 5f
                val lift = ((footY - shadowPoint[1]) / 100f).coerceIn(0f, 1f)
                val width = 301f + 38f * lift
                shadowPaint.color = 0xff271b17.toInt()
                shadowPaint.alpha = (53 - 23 * lift).toInt()
                shadowBounds.set(x - width, y - 37f, x + width, y + 37f)
                canvas.drawOval(shadowBounds, shadowPaint)
                shadowPaint.alpha = (83 - 54 * lift).toInt()
                shadowBounds.set(x - 236f, y - 22f, x + 236f, y + 22f)
                canvas.drawOval(shadowBounds, shadowPaint)
            }
            for (draw in ordered) draw(canvas, clip, time)
        }
    }

    companion object {
        suspend fun load(assets: AssetManager, id: String, full: Boolean): PetRig = withContext(Dispatchers.IO) {
            require(id.matches(Regex("[a-z][a-z0-9_]{2,63}")))
            val folder = "pets/rigs/$id/"
            val data = JSONObject(assets.open(folder + "rig.json").bufferedReader().use { it.readText() })
            val references = mutableSetOf<String>()
            if (full) {
                for ((_, layer) in data.getJSONObject("layers").entries()) {
                    references += layer.getString("src")
                    layer.optJSONObject("variants")?.entries()?.forEach { references += it.second.getString("src") }
                }
            } else references += data.getJSONObject("neutral").getString("src")
            val images = references.associateWith { file ->
                assets.open(folder + file).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                        ?: error("Нет изображения")
                }
            }
            val motion = try { JSONObject(assets.open("pets/motion.json").bufferedReader().use { it.readText() }) }
                catch (_: java.io.FileNotFoundException) { JSONObject() }
            PetRig(data, images, motion, full)
        }
    }
}

@Composable
internal fun AnimatedPet(
    rig: PetRig,
    gesture: PetGesture,
    modifier: Modifier,
    fits: List<PetFit> = emptyList(),
    room: Boolean = false,
) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active=state.isAtLeast(Lifecycle.State.STARTED)
    val motion=ValueAnimator.areAnimatorsEnabled()
    var elapsed by remember(rig) { mutableFloatStateOf(0f) }
    var idleTime by remember(rig) { mutableFloatStateOf(0f) }
    var idleClip by remember(rig) { androidx.compose.runtime.mutableStateOf("idle") }
    LaunchedEffect(rig,gesture,active,motion) {
        elapsed=0f
        idleTime=0f
        if(!active || !motion) return@LaunchedEffect
        suspend fun frames(duration: Float, frame: (Float)->Unit) {
            val start=withFrameNanos { it }
            var time=0f
            while(time<duration) {
                time=withFrameNanos{(it-start)/1_000_000f}
                frame(time.coerceAtMost(duration))
            }
        }
        if(gesture.clip!="idle") frames(rig.transitionDuration(gesture.clip)) { elapsed=it }
        if(rig.holds(gesture.clip)) {
            val settled = rig.transitionDuration(gesture.clip)
            val cycle = rig.duration(gesture.clip) - settled
            if(cycle > 0) while(true) {
                delay((900L..1700L).random())
                frames(cycle) { elapsed = settled + it }
            }
            return@LaunchedEffect
        }
        val options=listOf("blink","look","scratch","stretch","yawn")
        var previous=""
        while(true) {
            idleClip="idle"
            frames(rig.duration("idle")) { idleTime=it }
            delay((500L..1300L).random())
            val next=options.filter { it!=previous }.random()
            previous=next
            idleClip=next
            frames(rig.duration(next)) { idleTime=it }
        }
    }
    val drawPet = remember(rig, fits, room) { rig.renderer(fits, room) }
    Canvas(modifier) {
        val animated=active && motion
        val event=animated && gesture.clip!="idle" && (elapsed<rig.duration(gesture.clip) || rig.holds(gesture.clip))
        val staticSeat = room && !motion && gesture.clip == "sit"
        val clip = when {
            staticSeat || event -> gesture.clip
            animated -> idleClip
            else -> ""
        }
        val time = when {
            staticSeat -> rig.transitionDuration(clip)
            event -> elapsed
            else -> idleTime
        }
        drawIntoCanvas {
            val native=it.nativeCanvas
            if (room) {
                rig.drawGrounded(native, size.width, size.height) { drawPet(native, clip, time) }
            } else {
                val scale = minOf(size.width / rig.width, size.height / rig.height) * .89f
                native.save()
                native.translate((size.width - rig.width * scale) / 2, (size.height - rig.height * scale) / 2)
                native.scale(scale, scale)
                drawPet(native, clip, time)
                native.restore()
            }
        }
    }
}
