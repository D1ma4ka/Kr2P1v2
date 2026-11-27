package ru.krapiva.app.pets

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

private val Brass = Color(0xffe3b47a)
private val DarkWood = Color(0xff372620)
private val ShelfSlots = listOf("shelf_slot_01", "shelf_slot_02", "shelf_slot_03", "shelf_slot_04")
private val ForegroundSlots = listOf("floor_left", "floor_right", "table_top")

internal fun DrawScope.drawKraBar(
    sceneSize: Float,
    dark: Boolean,
    wardrobeOpen: Boolean,
    decor: Map<String, ImageBitmap>,
) = scale(size.width / sceneSize, size.height / sceneSize, Offset.Zero) {
    val wall = if (dark) Color(0xff263d39) else Color(0xff718477)
    val wallEdge = if (dark) Color(0xff142724) else Color(0xff485f55)
    drawRect(
        Brush.radialGradient(listOf(wall, wallEdge), Offset(168f, 102f), 245f),
        size = Size(sceneSize, sceneSize),
    )
    decor["wallpaper"]?.let { tile(it, 0f, 263f, .45f, 6) }
    for (x in 4..356 step 22) line(Color(0x2420322b), x.toFloat(), 0f, x.toFloat(), 265f)
    panel(wallEdge, 0f, 193f, 360f, 70f)
    line(Color(0x707da28d), 0f, 194f, 360f, 194f)
    for (x in 12..348 step 24) line(Color(0x5022332d), x.toFloat(), 198f, x.toFloat(), 259f)

    drawRect(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xff46312a), Color(0xff7b5644))
            else listOf(Color(0xff81604d), Color(0xffc09772)),
            259f, sceneSize,
        ),
        Offset(0f, 259f), Size(sceneSize, 101f),
    )
    decor["flooring"]?.let { tile(it, 263f, 97f, .62f, 5) }
    for (x in -190..550 step 57) {
        val end = x.toFloat()
        val start = 180f + (end - 180f) * .48f
        line(Color(0x603a261d), start, 260f, end, 360f)
        line(Color(0x50f1bd86), start + 1.5f, 260f, end + 2f, 360f, .7f)
    }
    for (y in listOf(267f, 283f, 307f, 338f)) line(Color(0x453a261d), 0f, y, 360f, y)
    panel(DarkWood, 0f, 255f, 360f, 8f)
    line(Color(0xffb68a5f), 0f, 255f, 360f, 255f)

    backBar(dark, decor)
    cupboard(wardrobeOpen)
    tastingTable()
    roomDecorAreas.filterKeys { it !in ShelfSlots && it !in ForegroundSlots }.forEach { (slot, area) ->
        decor[slot]?.let { artwork(it, area) }
    }
    if ("lighting" !in decor) pendant(dark, "wall_center" in decor)
}

internal fun DrawScope.drawKraBarForeground(
    sceneSize: Float,
    decor: Map<String, ImageBitmap>,
    food: ImageBitmap?,
) = scale(size.width / sceneSize, size.height / sceneSize, Offset.Zero) {
    ForegroundSlots.forEach { slot ->
        decor[slot]?.let { artwork(it, roomDecorAreas.getValue(slot)) }
    }
    food?.let { artwork(it, roomServingArea) }
}

private fun DrawScope.backBar(dark: Boolean, decor: Map<String, ImageBitmap>) {
    val honey = if (dark) Color(0xff977055) else Color(0xffb47d54)
    val cabinet = if (dark) Color(0xff4a6256) else Color(0xff59786b)
    shadow(49f, 238f, 237f, 38f)
    panel(Color(0x8018221d), 65f, 45f, 210f, 145f, 9f)
    panel(DarkWood, 69f, 43f, 203f, 137f, 8f)
    panel(honey, 73f, 46f, 195f, 129f, 6f)
    panel(Color(0xff2d3c32), 78f, 51f, 185f, 119f, 4f)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xff596052), Color(0xff283930)), 54f, 169f),
        Offset(81f, 54f), Size(179f, 111f),
    )
    for (row in 0..3) {
        val y = 61f + row * 27f
        line(Color(0x4431362a), 81f, y, 260f, y)
        for (column in 0..6) {
            val x = 88f + column * 28f + (row % 2) * 10f
            if (x < 260f) line(Color(0x4431362a), x, y, x, y + 27f)
        }
    }
    for (y in listOf(99f, 153f)) {
        panel(Color(0x42ffc075), 81f, y - 44, 179f, 44f)
        panel(DarkWood, 77f, y, 187f, 8f, 2f)
        panel(honey, 77f, y, 187f, 3f, 1f)
        line(Brass.copy(alpha = .6f), 80f, y, 261f, y, .9f)
    }
    bottle(139f, 98f, 24f, Color(0xff98854b))
    bottle(150f, 98f, 32f, Color(0xff678471))
    bottle(229f, 98f, 28f, Color(0xffa47c54))
    bottle(241f, 98f, 22f, Color(0xff7a905e))
    for (x in listOf(85f, 96f, 180f, 191f)) {
        panel(Color(0x538cb8a8), x, 141f, 8f, 11f, 1f)
        line(Color(0x8cd6dfb9), x, 141f, x + 8, 141f, .8f)
        line(Color(0x60d6dfb9), x + 1, 142f, x + 2, 150f, .8f)
    }
    ShelfSlots.forEach { slot -> decor[slot]?.let { artwork(it, roomDecorAreas.getValue(slot)) } }

    panel(DarkWood, 58f, 201f, 213f, 57f, 4f)
    panel(cabinet, 61f, 205f, 207f, 47f, 2f)
    for (x in listOf(65f, 132f, 199f)) {
        drawRoundRect(Color(0x8b22372e), Offset(x, 209f), Size(62f, 38f), CornerRadius(2f), style = Stroke(1.4f))
        line(Color(0x748eb39a), x + 3, 212f, x + 59, 212f)
        panel(Brass, x + 24, 216f, 14f, 2.5f, 1.25f)
    }
    polygon(honey, 45f, 195f, 63f, 177f, 267f, 177f, 282f, 195f)
    line(Color(0xffd6a675), 63f, 180f, 268f, 180f)
    line(Color(0x61452f24), 57f, 189f, 275f, 189f)
    panel(DarkWood, 45f, 193f, 237f, 11f, 3f)
    panel(honey, 45f, 193f, 237f, 3f, 2f)
    for (x in listOf(121f, 135f)) {
        panel(Color(0xffbb945e), x - 5, 182f, 12f, 3f, 1f)
        line(Brass, x, 182f, x, 163f, 3f)
        line(Brass, x, 166f, x + 6, 166f, 2.5f)
        line(Brass, x + 6, 166f, x + 6, 170f, 2f)
        panel(Color(0xff684832), x - 2, 156f, 4f, 8f, 1f)
    }
    panel(Color(0x8bd4d2b0), 223f, 169f, 10f, 10f, 1f)
    panel(Color(0x55fbc985), 225f, 174f, 6f, 4f, 1f)
}

private fun DrawScope.cupboard(open: Boolean) {
    val wood = Color(0xff885f47)
    val trim = Color(0xffad815e)
    shadow(269f, 268f, 88f, 25f)
    panel(DarkWood, 286f, 261f, 7f, 25f, 2f)
    panel(DarkWood, 336f, 261f, 7f, 25f, 2f)
    polygon(Color(0xff49352c), 271f, 133f, 282f, 119f, 285f, 273f, 271f, 259f)
    panel(DarkWood, 280f, 118f, 69f, 157f, 6f)
    panel(wood, 283f, 122f, 63f, 150f, 4f)
    panel(trim, 276f, 116f, 75f, 8f, 3f)
    panel(Color(0xff563e30), 287f, 131f, 55f, 107f, 2f)
    if (open) {
        panel(Color(0xff322b24), 290f, 134f, 49f, 101f, 2f)
        line(Brass, 291f, 151f, 337f, 151f)
        for ((x, fabric) in listOf(297f to Color(0xffd2b488), 313f to Color(0xff799a86), 325f to Color(0xffb16d5b))) {
            line(Brass, x, 151f, x, 158f)
            polygon(fabric, x, 157f, x + 8, 163f, x + 6, 184f, x - 7, 184f, x - 9, 163f)
        }
        panel(trim, 289f, 194f, 51f, 4f)
        panel(Color(0xff7d9a79), 297f, 218f, 23f, 6f, 2f)
        panel(Color(0xffccab7c), 299f, 224f, 22f, 6f, 2f)
        polygon(wood, 338f, 133f, 355f, 143f, 355f, 238f, 338f, 234f)
        line(Brass, 351f, 182f, 351f, 191f, 2f)
    } else {
        for (x in listOf(289f, 316f)) {
            panel(wood, x, 134f, 24f, 101f, 2f)
            drawRoundRect(trim, Offset(x + 3, 140f), Size(18f, 88f), CornerRadius(2f), style = Stroke(1f))
            line(Color(0x6556382b), x + 8, 154f, x + 8, 213f)
        }
        panel(Brass, 307f, 177f, 2.8f, 12f, 1.4f)
        panel(Brass, 320f, 177f, 2.8f, 12f, 1.4f)
    }
    panel(Color(0xff684a39), 289f, 243f, 51f, 22f, 2f)
    line(trim, 292f, 246f, 337f, 246f)
    panel(Brass, 308f, 252f, 13f, 2.5f, 1.25f)
}

private fun DrawScope.tastingTable() {
    shadow(12f, 296f, 95f, 21f)
    shadow(106f, 306f, 49f, 13f)
    panel(DarkWood, 119f, 279f, 5f, 31f, 2f)
    panel(DarkWood, 139f, 279f, 5f, 29f, 2f)
    line(Color(0xff875c44), 122f, 299f, 142f, 299f, 2f)
    panel(Color(0xff744837), 111f, 273f, 42f, 12f, 6f)
    panel(Color(0xffad7455), 111f, 272f, 42f, 7f, 4f)
    panel(DarkWood, 23f, 246f, 7f, 61f, 3f)
    panel(DarkWood, 84f, 245f, 7f, 60f, 3f)
    line(Color(0xff855c43), 27f, 287f, 87f, 287f, 3f)
    polygon(Color(0xffb68255), 11f, 242f, 26f, 226f, 84f, 226f, 101f, 242f)
    panel(DarkWood, 11f, 240f, 90f, 12f, 4f)
    panel(Color(0xffb68255), 11f, 240f, 90f, 3f, 1.5f)
    drawOval(Color(0xff503e32), Offset(56f, 233f), Size(33f, 7f))
    drawOval(Color(0xffe2c49d), Offset(57f, 231f), Size(31f, 7f))
    drawOval(Color(0xffb8926f), Offset(62f, 233f), Size(21f, 3f))
}

private fun DrawScope.pendant(dark: Boolean, centerDecor: Boolean) {
    val x = if (centerDecor) 243f else 180f
    val glow = if (dark) 0x56ffc67e else 0x30f9d09f
    drawCircle(
        Brush.radialGradient(listOf(Color(glow), Color.Transparent), Offset(x, 41f), 74f),
        74f, Offset(x, 41f),
    )
    line(Color(0xff463f31), x, 0f, x, 23f, 2f)
    drawOval(Color(0xfff7d495), Offset(x - 17f, 32f), Size(34f, 9f))
    polygon(Color(0xff936b4a), x - 21, 34f, x - 11, 23f, x + 11, 23f, x + 21, 34f)
    line(Brass, x - 20, 34f, x + 20, 34f, 1.5f)
}

private fun DrawScope.bottle(x: Float, y: Float, height: Float, tint: Color) {
    panel(tint, x, y - height + 8f, 9f, height - 8f, 2.5f)
    panel(tint, x + 2.4f, y - height, 4f, 11f, 1f)
    panel(Brass.copy(alpha = .7f), x + 2, y - height, 5f, 2f)
    line(Color(0x72e1d9ab), x + 2, y - height + 12, x + 2, y - 4, 1f)
    panel(Color(0xaac4a47c), x + 3.5f, y - 11, 5f, 7f, .8f)
}

private fun DrawScope.tile(image: ImageBitmap, y: Float, height: Float, alpha: Float, columns: Int) {
    val width = 360f / columns
    val rowHeight = width * image.height / image.width
    clipRect(0f, y, 360f, y + height) {
        for (row in 0..(height / rowHeight).toInt()) for (column in 0 until columns) {
            drawImage(image, dstOffset = IntOffset((column * width).roundToInt(), (y + row * rowHeight).roundToInt()),
                dstSize = IntSize(width.roundToInt() + 1, rowHeight.roundToInt() + 1), alpha = alpha)
        }
    }
}

private fun DrawScope.artwork(image: ImageBitmap, area: RoomArea) {
    val scale = min(area.width / image.width, area.height / image.height)
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    drawImage(image,
        dstOffset = IntOffset((area.x + (area.width - width) / 2).roundToInt(), (area.y + area.height - height).roundToInt()),
        dstSize = IntSize(width, height),
    )
}

private fun DrawScope.shadow(x: Float, y: Float, width: Float, height: Float) {
    drawOval(Color(0x361c1611), Offset(x, y), Size(width, height))
    drawOval(Color(0x271c1611), Offset(x + width * .12f, y + height * .15f), Size(width * .76f, height * .7f))
}

private fun DrawScope.panel(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float = 0f) =
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(radius))

private fun DrawScope.line(color: Color, x: Float, y: Float, toX: Float, toY: Float, width: Float = 1f) =
    drawLine(color, Offset(x, y), Offset(toX, toY), width, StrokeCap.Round)

private fun DrawScope.polygon(color: Color, vararg points: Float) {
    drawPath(Path().apply {
        moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
        close()
    }, color)
}
