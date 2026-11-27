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
import androidx.compose.ui.platform.LocalContext
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
    private val data: JSONObject,
    private val images: Map<String,Bitmap>,
    private val additional: JSONObject
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layers = data.getJSONObject("layers").entries().map { it.second }
    private val bones = data.getJSONObject("bones").entries()
    val anchors = data.getJSONObject("anchors")
    val width = data.point("canvas").first
    val height = data.point("canvas").second

    private fun clip(name: String) = data.getJSONObject("clips").optJSONObject(name) ?: additional.optJSONObject(name)
    fun holds(name: String): Boolean = clip(name)?.optBoolean("hold")==true
    fun duration(name: String): Float = clip(name)?.number("duration") ?: 600f

    private fun keyframes(keys: JSONArray?, time: Float): Triple<JSONObject,JSONObject,Float> {
        if(keys==null || keys.length()==0) return Triple(JSONObject(),JSONObject(),0f)
        val first=keys.getJSONObject(0)
        if(time<=first.number("time")) return Triple(first,first,0f)
        for(i in 1 until keys.length()) {
            val right=keys.getJSONObject(i)
            if(time<right.number("time")) {
                val left=keys.getJSONObject(i-1)
                return Triple(left,right,(time-left.number("time"))/(right.number("time")-left.number("time")))
            }
        }
        val last=keys.getJSONObject(keys.length()-1)
        return Triple(last,last,0f)
    }

    private fun drawSprite(canvas: AndroidCanvas, sprite: JSONObject) {
        val bitmap=images[sprite.getString("src")] ?: return
        val (x,y)=sprite.point("offset")
        val (w,h)=sprite.point("size")
        canvas.drawBitmap(bitmap,null,RectF(x,y,x+w,y+h),paint)
    }

    fun draw(canvas: AndroidCanvas, name: String, elapsed: Float, fits: List<PetFit>) {
        if(images.size == 1) {
            drawSprite(canvas,data.getJSONObject("neutral"))
            drawFits(canvas, mapOf("root" to Matrix()), fits)
            return
        }
        val clip=clip(name)
        val duration=duration(name)
        val time=if(clip?.optBoolean("loop")==true) elapsed%duration else elapsed.coerceAtMost(duration)
        val tracks=clip?.optJSONObject("tracks")
        val variants=clip?.optJSONObject("variants")
        val transforms=mutableMapOf<String,Matrix>()
        val allBones=bones.toMap()
        fun matrix(bone: String): Matrix = transforms.getOrPut(bone) {
            val metadata=allBones.getValue(bone)
            val (px,py)=metadata.point("pivot")
            val (left,right,f)=keyframes(tracks?.optJSONArray(bone),time)
            fun value(field: String, fallback: Float=0f): Float {
                val start=left.number(field,fallback)
                return start+(right.number(field,fallback)-start)*f
            }
            val local=Matrix().apply {
                postTranslate(-px,-py)
                postScale(value("scaleX",1f),value("scaleY",1f))
                postRotate(value("rotation"))
                postTranslate(px+value("x"),py+value("y"))
            }
            metadata.optString("parent").takeIf(String::isNotEmpty)?.let { local.postConcat(matrix(it)) }
            local
        }
        bones.forEach { matrix(it.first) }
        val actions=mutableListOf<Pair<Int,()->Unit>>()
        for(layer in layers) {
            val bone=layer.getString("bone")
            val variant=keyframes(variants?.optJSONArray(bone),time).first.optString("value")
            val sprite=layer.optJSONObject("variants")?.optJSONObject(variant) ?: layer
            actions += layer.getInt("depth") to {
                canvas.save(); canvas.concat(matrix(bone)); drawSprite(canvas,sprite); canvas.restore()
            }
        }
        for(fit in fits) {
            val slot=anchors.optJSONObject(fit.slot) ?: continue
            actions += slot.getInt("depth") to { drawFit(canvas,transforms,slot,fit) }
        }
        actions.sortedBy { it.first }.forEach { it.second() }
    }

    private fun drawFits(canvas: AndroidCanvas, transforms: Map<String,Matrix>, fits: List<PetFit>) {
        fits.sortedBy { anchors.optJSONObject(it.slot)?.optInt("depth") ?: 0 }.forEach { fit ->
            anchors.optJSONObject(fit.slot)?.let { drawFit(canvas,transforms,it,fit) }
        }
    }

    private fun drawFit(canvas: AndroidCanvas, transforms: Map<String,Matrix>, anchor: JSONObject, fit: PetFit) {
        val (x,y)=anchor.point("position")
        val render=fit.render
        canvas.save()
        transforms[anchor.getString("bone")]?.let(canvas::concat)
        canvas.translate(x+render.number("dx"),y+render.number("dy"))
        canvas.rotate(anchor.number("rotation")+render.number("rotation"))
        val scale=anchor.number("scale",1f)*render.number("scale",1f)
        canvas.scale(scale,scale)
        val w=render.number("displayWidth",300f)
        val h=w*fit.bitmap.height/fit.bitmap.width
        val ox=w*render.number("originX",.5f)
        val oy=h*render.number("originY",.5f)
        canvas.drawBitmap(fit.bitmap,null,RectF(-ox,-oy,w-ox,h-oy),paint)
        canvas.restore()
    }

    companion object {
        suspend fun load(assets: AssetManager,id: String,full: Boolean): PetRig = withContext(Dispatchers.IO) {
            require(id.matches(Regex("[a-z][a-z0-9_]{2,63}")))
            val folder="pets/rigs/$id/"
            val data=JSONObject(assets.open(folder+"rig.json").bufferedReader().use { it.readText() })
            val references=mutableSetOf(data.getJSONObject("neutral").getString("src"))
            if(full) for((_,layer) in data.getJSONObject("layers").entries()) {
                references += layer.getString("src")
                layer.optJSONObject("variants")?.entries()?.forEach { references += it.second.getString("src") }
            }
            val images=references.associateWith { file ->
                assets.open(folder+file).use { BitmapFactory.decodeStream(it,null,
                    BitmapFactory.Options().apply { inSampleSize=2 }) ?: error("Нет изображения") }
            }
            val motion=try { JSONObject(assets.open("pets/motion.json").bufferedReader().use { it.readText() }) }
                catch(_: java.io.FileNotFoundException) { JSONObject() }
            PetRig(data,images,motion)
        }
    }
}

@Composable
internal fun AnimatedPet(rig: PetRig, gesture: PetGesture, modifier: Modifier, fits: List<PetFit> = emptyList()) {
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
        if(gesture.clip!="idle") frames(rig.duration(gesture.clip)) { elapsed=it }
        if(rig.holds(gesture.clip)) return@LaunchedEffect
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
    Canvas(modifier) {
        val animated=active && motion
        val event=animated && gesture.clip!="idle" && (elapsed<rig.duration(gesture.clip) || rig.holds(gesture.clip))
        val clip=if(!animated) "" else if(event) gesture.clip else idleClip
        val time=if(event) elapsed else idleTime
        val scale=minOf(size.width/rig.width,size.height/rig.height)*.89f
        drawIntoCanvas {
            val native=it.nativeCanvas
            native.save()
            native.translate((size.width-rig.width*scale)/2,(size.height-rig.height*scale)/2)
            native.scale(scale,scale)
            rig.draw(native,clip,time,fits)
            native.restore()
        }
    }
}
