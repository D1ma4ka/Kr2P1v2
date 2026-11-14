package ru.krapiva.app.account

import org.junit.Assert.*
import org.junit.Test

class EmailVerificationCodeTest {
    @Test fun pasteKeepsSixAsciiDigitsAndLeadingZeroes() {
        assertEquals("001234", EmailVerificationCode.input(" 00-12 34\n567"))
        assertEquals("", EmailVerificationCode.input("abcdef１２３４５６"))
        assertTrue(EmailVerificationCode.isComplete("000007"))
    }
    @Test fun partialOverlongAndPasswordCannotBeSubmitted() {
        for (value in listOf("", "12345", "1234567", "12a456", "１２３４５６", "password123456")) {
            assertFalse(value, EmailVerificationCode.isComplete(value))
        }
    }
}
