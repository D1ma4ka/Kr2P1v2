package ru.krapiva.app.account

/** Presentation only: keep connection/validation errors, hide mail transport details. */
internal fun visibleAuthError(message: String): String =
    if (message.contains("Mailpit", ignoreCase = true))
        "Сейчас не удаётся отправить письмо на этот адрес. Обратитесь в поддержку."
    else message
