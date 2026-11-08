package ru.krapiva.app.catalog

/** JSON does not allow NaN or infinity; validate before constructing an upload body. */
internal fun parseFiniteNumber(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
