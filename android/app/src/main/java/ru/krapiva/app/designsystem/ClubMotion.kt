package ru.krapiva.app.designsystem

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sin

/** Finite foreground-only animation. No sensors, no jobs left running after the 1.8s reveal. */
@Composable fun ClubBeer(modifier: Modifier = Modifier, backdrop: Boolean = false, still: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    var progress by remember { mutableFloatStateOf(if(still || reduced) 1f else 0f) }
    DisposableEffect(owner,context) {
        val observer = LifecycleEventObserver { _,_ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        val settings = object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        owner.lifecycle.addObserver(observer)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,settings)
        onDispose { owner.lifecycle.removeObserver(observer);context.contentResolver.unregisterContentObserver(settings) }
    }
    LaunchedEffect(active,reduced,still) {
        if(still || reduced) {progress = 1f;return@LaunchedEffect}
        if(!active || progress >= 1f) return@LaunchedEffect
        val start = withFrameNanos { it } - (progress * 1800_000_000L).toLong()
        while(progress < 1f) withFrameNanos { progress = ClubMotion.progress((it-start)/1_000_000) }
    }
    val cream = Color(0xFFFFEFCE)
    Canvas(modifier.semantics { contentDescription = if(backdrop) "Янтарное пиво, пена и пузырьки" else "Фирменный бокал KraPiva" }) {
        val w=size.width;val h=size.height
        val glass=Path().apply {
            if(backdrop) addRect(androidx.compose.ui.geometry.Rect(0f,0f,w,h))
            else {moveTo(w*.20f,h*.08f);cubicTo(w*.08f,h*.30f,w*.27f,h*.76f,w*.28f,h*.88f);quadraticTo(w*.5f,h*.97f,w*.72f,h*.88f);cubicTo(w*.73f,h*.76f,w*.92f,h*.30f,w*.80f,h*.08f);close()}
        }
        val top=h*(if(backdrop) .16f else .18f)+h*.7f*(1f-progress)
        clipPath(glass) {
            drawPath(glass,Color(0xFFEDA13A).copy(alpha=.13f))
            val liquid=Path().apply {moveTo(0f,top);cubicTo(w*.3f,top-h*.025f,w*.7f,top+h*.025f,w,top);lineTo(w,h);lineTo(0f,h);close()}
            drawPath(liquid,Brush.verticalGradient(listOf(Color(0xFFFFC34D),Color(0xFFCE7107),Color(0xFFEEAC32)),startY=top,endY=h))
            for(i in 0 until 54) {
                val x=w*((i*37%97)+1)/100f
                val y=top+(h-top)*((i*19%97)/97f)-sin(progress*5+i.toFloat())*h*.02f
                if(y>top+h*.025f) drawCircle(cream.copy(alpha=.55f),w*(.0025f+(i%4)*.0012f),Offset(x,y),style=Stroke(w*.0015f))
            }
            val foam=Path().apply {moveTo(0f,top);cubicTo(w*.3f,top-h*.025f,w*.7f,top+h*.025f,w,top)}
            drawPath(foam,cream,style=Stroke(h*.055f))
            for(i in 0..30) drawCircle(Color.White.copy(alpha=.35f),w*.009f,Offset(w*i/30,top+sin(i.toFloat())*h*.008f))
        }
        if(!backdrop) {
            drawPath(glass,cream.copy(alpha=.8f),style=Stroke(w*.012f))
            drawLine(Color.White.copy(alpha=.45f),Offset(w*.25f,h*.28f),Offset(w*.34f,h*.82f),w*.018f)
            drawLine(cream,Offset(w*.30f,h*.92f),Offset(w*.70f,h*.92f),w*.016f)
        }
    }
}
