package ru.krapiva.app.account

/** Keep leading zeroes; the API accepts exactly six ASCII digits, never a password. */
object EmailVerificationCode {
    fun input(value: String): String = value.filter { it in '0'..'9' }.take(6)
    fun isComplete(value: String): Boolean = value.length == 6 && value.all { it in '0'..'9' }
}
