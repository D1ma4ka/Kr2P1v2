package ru.krapiva.app.diary

import ru.krapiva.app.account.AccountApi

internal enum class CheckinVisibility(val value: String, val label: String) {
    PRIVATE("private", "Только я"),
    FRIENDS("friends", "Друзья"),
    PUBLIC("public", "Все");
}

internal suspend fun AccountApi.defaultCheckinVisibility(): CheckinVisibility {
    val value = localDocument("checkin-preferences").optString("visibility")
    return CheckinVisibility.entries.firstOrNull { it.value == value } ?: CheckinVisibility.PRIVATE
}

internal suspend fun AccountApi.saveDefaultCheckinVisibility(visibility: CheckinVisibility) {
    editLocalDocument("checkin-preferences") { it.put("visibility", visibility.value) }
}
