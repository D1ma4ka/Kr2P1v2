package ru.krapiva.app.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun missingOrUnknownPreferencesFollowAndroid() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference("obsolete"))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test fun existingExplicitPreferencesStillOverrideAndroid() {
        assertTrue(ThemeMode.fromPreference("DARK").isDark(systemDark = false))
        assertFalse(ThemeMode.fromPreference("LIGHT").isDark(systemDark = true))
    }
}
