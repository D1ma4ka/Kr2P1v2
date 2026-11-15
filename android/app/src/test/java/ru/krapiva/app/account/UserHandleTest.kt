package ru.krapiva.app.account

import org.junit.Assert.*
import org.junit.Test

class UserHandleTest {
    @Test fun asciiRulesAndNormalization() {
        for (handle in listOf("CraftLover", "beer_fan", "krapiva2026", "a12", "a".repeat(24))) {
            assertTrue(handle, UserHandle.valid(handle))
            assertEquals(handle.lowercase(java.util.Locale.ROOT), UserHandle.normalize(handle))
        }
        for (handle in listOf("ab", "a".repeat(25), "1abc", "_abc", "abc_", "ab__cd", " abcd", "abcd ", "абв", "beer🍺", "@beer"))
            assertFalse(handle, UserHandle.valid(handle))
    }
}
