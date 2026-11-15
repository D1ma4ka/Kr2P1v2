package ru.krapiva.app.account

/** Matches the server's required fields; passwords are never trimmed or truncated. */
internal object AuthValidation {
    fun email(value: String): Boolean = value.trim().let {
        it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)
    }
    fun passwordLength(value: String): Int = value.codePointCount(0, value.length)
    fun password(value: String): Boolean = passwordLength(value) in 8..128
    fun registrationReady(email: String, name: String, password: String, confirm: String, adult: Boolean): Boolean =
        email.isNotBlank() && name.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty() && adult
    fun registrationError(email: String, name: String, password: String, confirm: String, adult: Boolean): String? =
        if (password != confirm) "Пароли не совпадают"
        else registrationIssues(email, name, password, confirm, adult).firstOrNull()
    fun registrationIssues(email: String, name: String, password: String, confirm: String, adult: Boolean): List<String> = buildList {
        if (!email(email)) add("Введите корректный e-mail")
        if (!UserHandle.valid(name)) add("Никнейм: 3–24 символа a-z, 0-9 и _, первая — буква; без __ и _ в конце")
        if (!password(password)) add(if (passwordLength(password) > 128) "Пароль слишком длинный" else "Пароль — от 8 символов")
        if (confirm != password || confirm.isEmpty()) add("Пароли должны совпадать")
        if (!adult) add("Подтвердите, что вам исполнилось 18 лет")
    }
}
