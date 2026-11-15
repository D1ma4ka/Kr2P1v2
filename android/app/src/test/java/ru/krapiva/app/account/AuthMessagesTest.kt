package ru.krapiva.app.account

import org.junit.Assert.*
import org.junit.Test

class AuthMessagesTest {
    @Test fun localMailFailureRemainsAnErrorWithoutExposingDevelopmentDetails() {
        val visible = visibleAuthError("Локальная почтовая среда: используйте адрес name@example.invalid и откройте письмо в Mailpit. Доставка во внешнюю почту не настроена.")
        assertTrue(visible.contains("не удаётся отправить письмо"))
        for (detail in listOf("Mailpit", "example.invalid", "Локальная")) assertFalse(visible.contains(detail))
    }

    @Test fun connectionAndAccountErrorsRemainIntact() {
        for (message in listOf("Нет связи с сервером. Проверьте сеть и повторите.",
            "Почта временно недоступна; повторите позже", "Неверные данные входа или код", "Слишком много попыток; повторите позже")) {
            assertEquals(message, visibleAuthError(message))
        }
    }
}
