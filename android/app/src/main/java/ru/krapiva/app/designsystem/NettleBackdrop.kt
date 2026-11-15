package ru.krapiva.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/** Static vector decoration behind content. No input/semantics layer or animation work. */
@Composable
fun KraPivaBackdrop(modifier: Modifier = Modifier, authPattern: Boolean = false, content: @Composable () -> Unit) {
    val dark = LocalThemeMode.current == ThemeMode.DARK
    val leaf = remember {
        Path().apply {
            moveTo(0f, -40f)
            lineTo(9f, -28f); lineTo(6f, -28f); lineTo(18f, -16f); lineTo(13f, -17f)
            lineTo(24f, -3f); lineTo(18f, -6f); lineTo(23f, 9f); lineTo(17f, 6f)
            lineTo(17f, 20f); lineTo(11f, 17f); quadraticTo(8f, 28f, 0f, 31f)
            quadraticTo(-8f, 28f, -11f, 17f); lineTo(-17f, 20f); lineTo(-17f, 6f)
            lineTo(-23f, 9f); lineTo(-18f, -6f); lineTo(-24f, -3f); lineTo(-13f, -17f)
            lineTo(-18f, -16f); lineTo(-6f, -28f); lineTo(-9f, -28f); close()
        }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box {
            if (dark || authPattern) Canvas(Modifier.matchParentSize()) {
                val tile = 164.dp.toPx()
                val scale = density * .78f
                val ink = if (authPattern) {
                    if (dark) Color(0xFFB0C9A5).copy(alpha = .18f) else Color(0xFF456A16).copy(alpha = .16f)
                } else Color(0xFF9CB79E).copy(alpha = .085f)
                clipRect {
                    for (row in -1..(size.height / tile).toInt() + 1) {
                        for (column in -1..(size.width / tile).toInt() + 1) {
                            withTransform({
                                translate(column * tile + if (row % 2 == 0) tile * .2f else tile * .7f, row * tile + tile * .5f)
                                rotate(if ((row + column) % 2 == 0) -32f else 28f, Offset.Zero)
                                scale(scale, scale, Offset.Zero)
                            }) {
                                drawPath(leaf, ink, style = Stroke(.9f))
                                drawLine(ink, Offset(0f, 40f), Offset(0f, -33f), .8f)
                                for (y in listOf(-14f, 1f, 15f)) {
                                    drawLine(ink, Offset(0f, y + 9f), Offset(12f, y - 5f), .7f)
                                    drawLine(ink, Offset(0f, y + 9f), Offset(-12f, y - 5f), .7f)
                                }
                            }
                        }
                    }
                }
            }
            content()
        }
    }
}
