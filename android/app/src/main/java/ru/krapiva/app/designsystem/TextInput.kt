package ru.krapiva.app.designsystem

internal fun String.takeCodePoints(limit: Int): String =
    if (codePointCount(0, length) <= limit) this else substring(0, offsetByCodePoints(0, limit))
