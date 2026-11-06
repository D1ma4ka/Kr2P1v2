package ru.krapiva.app.catalog

/** Pure domain validation shared by camera and manual entry. QR never enters this path. */
object Gtin {
    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.length !in setOf(8,12,13,14) || value.any { it !in '0'..'9' }) return null
        val padded = value.padStart(14, '0')
        return padded.takeIf { it.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum() % 10 == 0 }
    }
}

/** Rotate the copied camera luma clockwise; independent of camera/display orientation. */
internal fun rotateLuma(data: ByteArray, width: Int, height: Int): ByteArray = ByteArray(data.size).also { out ->
    for (y in 0 until height) for (x in 0 until width) out[x * height + height - 1 - y] = data[y * width + x]
}
