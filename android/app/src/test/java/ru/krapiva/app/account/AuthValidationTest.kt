package ru.krapiva.app.account

import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    @Test fun registrationButtonDependsOnFilledFieldsAndAgeInsteadOfPasswordMatch() {
        val values = listOf("user@example.invalid", "Имя", "Correct password!", "Different password!")
        fun ready(fields: List<String>, adult: Boolean = true) =
            AuthValidation.registrationReady(fields[0], fields[1], fields[2], fields[3], adult)
        assertTrue(ready(values))
        for (index in values.indices) assertFalse(ready(values.toMutableList().apply { this[index] = "" }))
        assertFalse(ready(values, false))
        assertFalse(ready(values.toMutableList().apply { this[1] = "  " }))
        assertEquals("Пароли не совпадают", AuthValidation.registrationError(values[0], values[1], values[2], values[3], true))
        assertNull(AuthValidation.registrationError(values[0], values[1], values[2], values[2], true))
        assertTrue(AuthValidation.registrationReady(values[0], values[1], "short", "short", true))
        assertEquals("Пароль: От 12 символов", AuthValidation.registrationError(values[0], values[1], "short", "short", true))
    }

    @Test fun registrationExplainsEveryBlockingRequirement() {
        val password = "Correct password!"
        assertTrue(AuthValidation.registrationIssues(" user@example.invalid ", "Имя", password, password, true).isEmpty())
        assertEquals(5, AuthValidation.registrationIssues("invalid", " ", "short", "different", false).size)
        assertFalse(AuthValidation.email("a b@example.invalid"))
        assertFalse(AuthValidation.email("a@@example.invalid"))
        assertFalse(AuthValidation.password("a".repeat(11)))
        assertTrue(AuthValidation.password("a".repeat(12)))
        assertTrue(AuthValidation.password("a".repeat(128)))
        assertFalse(AuthValidation.password("a".repeat(129)))
        // JSON Schema minLength/maxLength count Unicode code points, not UTF-16 units.
        assertFalse(AuthValidation.password("🍺".repeat(6)))
        assertTrue(AuthValidation.password("🍺".repeat(12)))
        assertTrue(AuthValidation.password("🍺".repeat(128)))
        assertFalse(AuthValidation.password("🍺".repeat(129)))
        assertFalse(AuthValidation.registrationIssues("user@example.invalid", "Имя", password, password, false).isEmpty())
    }
}
