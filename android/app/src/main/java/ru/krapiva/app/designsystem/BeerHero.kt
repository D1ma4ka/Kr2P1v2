package ru.krapiva.app.designsystem

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.tan

@Composable
fun BeerHero(modifier: Modifier = Modifier, previewAngle: Float? = null, forceReducedMotion: Boolean = false, forceNoSensor: Boolean = false) {
    val motion = rememberLiquidAnimation(forceReducedMotion, previewAngle, forceNoSensor)
    val hop = remember { PathParser().parsePathString("M54,45C37,47 35,61 42,75Q47,84 54,88Q61,84 66,75C73,61 71,47 54,45Z").toPath() }
    val scales = remember { PathParser().parsePathString("M54,45V40 M54,48Q48,59 40,60 M54,48Q60,59 68,60 M40,59Q43,70 54,72Q65,70 68,59 M44,70Q47,80 54,85Q61,80 64,70 M54,62Q49,67 47,74 M54,62Q59,67 61,74").toPath() }
    val amber = ClubTokens.Amber
    val cream = androidx.compose.ui.graphics.Color(0xFFF4EEE4)
    val ink = ClubTokens.OnAmber
    Canvas(modifier.size(112.dp).semantics { contentDescription = "Фирменный бокал KraPiva с пеной и хмелем" }) {
        val w = size.width; val h = size.height
        val glass = Path().apply {
            moveTo(w * .23f, h * .2f); lineTo(w * .77f, h * .2f)
            lineTo(w * .69f, h * .87f); quadraticTo(w * .5f, h * .93f, w * .31f, h * .87f); close()
        }
        val delta = tan(Math.toRadians(motion.angle.value.toDouble())).toFloat() * w
        val liquid = Path().apply {
            moveTo(0f, h * .4f - delta / 2); cubicTo(w * .3f, h * .37f, w * .7f, h * .43f, w, h * .4f + delta / 2)
            lineTo(w, h); lineTo(0f, h); close()
        }
        clipPath(glass) {
            drawPath(liquid, amber)
            val foam = Path().apply { moveTo(0f, h * .4f - delta / 2); cubicTo(w * .3f, h * .37f, w * .7f, h * .43f, w, h * .4f + delta / 2) }
            drawPath(foam, cream, style = Stroke(h * .075f))
        }
        drawPath(glass, cream, style = Stroke(w * .025f))
        // Original hop cone with overlapping scales, shared with static brand artwork.
        withTransform({ scale(w / 108f, h / 108f, pivot = Offset.Zero) }) {
            drawPath(hop, KraPivaColors.Hop)
            drawPath(scales, ink, style = Stroke(2f))
        }
        for (side in listOf(0, 1)) for (vertical in listOf(0, 1)) {
            val x = if (side == 0) w * .06f else w * .94f
            val y = if (vertical == 0) h * .08f else h * .96f
            val dx = if (side == 0) w * .13f else -w * .13f
            val dy = if (vertical == 0) h * .12f else -h * .12f
            drawLine(cream, Offset(x, y), Offset(x + dx, y), w * .025f)
            drawLine(cream, Offset(x, y), Offset(x, y + dy), w * .025f)
        }
    }
}
