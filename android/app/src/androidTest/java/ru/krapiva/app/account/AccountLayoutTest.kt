package ru.krapiva.app.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.designsystem.KraPivaTheme

class AccountLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun narrowLargeFontRegistrationKeepsFieldsAndActionReachable() {
        val store = AccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear(); store.close()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                KraPivaTheme { Box(Modifier.width(320.dp)) { AccountApp() } }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Создать аккаунт").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performScrollTo().performTextInput("layout@example.invalid")
        compose.onNodeWithText("Пароль · От 12 символов").performScrollTo().performTextInput("Layout synthetic password!")
        compose.onNodeWithText("Повторите пароль").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Зарегистрироваться").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth-beer").assertExists()
    }
}
