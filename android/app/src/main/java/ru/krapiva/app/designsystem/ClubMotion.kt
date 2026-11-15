package ru.krapiva.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.sin

/** Loading glass; celebration backdrops keep their finite reveal. Auth forms use the logo. */
@Composable fun ClubBeer(
    modifier: Modifier = Modifier, backdrop: Boolean = false, still: Boolean = false,
    previewAngle: Float? = null, forceNoSensor: Boolean = false,
    reveal: Boolean = true,
    animateBackdrop: Boolean = false,
) {
    val motion = rememberLiquidAnimation(still, previewAngle, forceNoSensor, reveal = reveal, continuous = !backdrop || animateBackdrop)
    val cream = Color(0xFFFFEFCE)
    Canvas(modifier.semantics { contentDescription = if(backdrop) "Янтарное пиво, пена и пузырьки" else "Фирменный бокал KraPiva" }) {
        val w=size.width;val h=size.height
        val progress = motion.fill.value
        val phase = motion.phase.value
        val delta = kotlin.math.tan(Math.toRadians(motion.angle.value.toDouble())).toFloat() * w
        val wave = h * .012f * sin(phase * 1.6f)
        fun surface() = Path().apply {
            moveTo(0f,topY(progress, h, backdrop)-delta/2)
            val top = topY(progress, h, backdrop)
            cubicTo(w*.3f,top-delta*.2f-wave,w*.7f,top+delta*.2f+wave,w,top+delta/2)
        }
        val glass=Path().apply {
            if(backdrop) addRect(androidx.compose.ui.geometry.Rect(0f,0f,w,h))
            else {moveTo(w*.20f,h*.08f);cubicTo(w*.08f,h*.30f,w*.27f,h*.76f,w*.28f,h*.88f);quadraticTo(w*.5f,h*.97f,w*.72f,h*.88f);cubicTo(w*.73f,h*.76f,w*.92f,h*.30f,w*.80f,h*.08f);close()}
        }
        val top=h*(if(backdrop) .16f else .18f)+h*.7f*(1f-progress)
        clipPath(glass) {
            drawPath(glass,Color(0xFFEDA13A).copy(alpha=.13f))
            val liquid=surface().apply {lineTo(w,h);lineTo(0f,h);close()}
            drawPath(liquid,Brush.verticalGradient(listOf(Color(0xFFFFC34D),Color(0xFFCE7107),Color(0xFFEEAC32)),startY=top,endY=h))
            for(i in 0 until 54) {
                val x=w*((i*37%97)+1)/100f
                val y=top+(h-top)*(((i*19%97)/97f + 1f - (phase*.055f)%1f)%1f)
                if(y>top+h*.025f) drawCircle(cream.copy(alpha=.55f),w*(.0025f+(i%4)*.0012f),Offset(x,y),style=Stroke(w*.0015f))
            }
            val foam=surface()
            drawPath(foam,cream,style=Stroke(h*.055f))
            for(i in 0..30) drawCircle(Color.White.copy(alpha=.35f),w*.009f,Offset(w*i/30,top+delta*(i/30f-.5f)+sin(i.toFloat()+phase)*h*.008f))
        }
        if(!backdrop) {
            drawPath(glass,cream.copy(alpha=.8f),style=Stroke(w*.012f))
            drawLine(Color.White.copy(alpha=.45f),Offset(w*.25f,h*.28f),Offset(w*.34f,h*.82f),w*.018f)
            drawLine(cream,Offset(w*.30f,h*.92f),Offset(w*.70f,h*.92f),w*.016f)
        }
    }
}

private fun topY(progress: Float, height: Float, backdrop: Boolean): Float =
    height * (if (backdrop) .16f else .18f) + height * .7f * (1f - progress)
