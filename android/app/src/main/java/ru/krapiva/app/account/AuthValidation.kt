package ru.krapiva.app.account

/** Matches the server's required fields; passwords are never trimmed or truncated. */
internal object AuthValidation {
    fun email(value: String): Boolean = value.trim().let {
        it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)
    }
    fun passwordLength(value: String): Int = value.codePointCount(0, value.length)
    fun password(value: String): Boolean = passwordLength(value) in 12..128
    fun registrationIssues(email: String, name: String, password: String, confirm: String, adult: Boolean): List<String> = buildList {
        if (!email(email)) add("Введите корректный e-mail")
        if (name.isBlank() || name.length > 80) add("Укажите отображаемое имя")
        if (!password(password)) add(if (passwordLength(password) > 128) "Пароль слишком длинный" else "Пароль: От 12 символов")
        if (confirm != password || confirm.isEmpty()) add("Пароли должны совпадать")
        if (!adult) add("Подтвердите, что вам исполнилось 18 лет")
    }
}
