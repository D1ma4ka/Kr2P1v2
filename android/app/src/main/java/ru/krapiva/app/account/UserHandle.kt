package ru.krapiva.app.account

internal object UserHandle {
    fun normalize(value: String) = value.lowercase(java.util.Locale.ROOT)
    fun valid(value: String) = Regex("^[a-zA-Z][a-zA-Z0-9_]{1,22}[a-zA-Z0-9]$").matches(value) && !value.contains("__")
}
