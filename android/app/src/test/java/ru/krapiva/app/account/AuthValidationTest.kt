package ru.krapiva.app.account

import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
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
