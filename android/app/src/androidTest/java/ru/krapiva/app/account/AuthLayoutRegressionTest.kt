package ru.krapiva.app.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.designsystem.*

class AuthLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactLargeFontBothThemesKeepActionsAndLongEmailReachable() {
        AccountStore(InstrumentationRegistry.getInstrumentation().targetContext).let {it.clear();it.close()}
        lateinit var model: AccountViewModel
        var mode by mutableStateOf(ThemeMode.DARK)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                KraPivaTheme(mode) {Box(Modifier.width(320.dp)) {model=viewModel();AccountApp(model)}}
            }
        }
        compose.waitUntil(15_000) {model.state.value.screen==AccountScreen.WELCOME}
        val email="a".repeat(60)+"@"+"b".repeat(60)+".example.invalid"
        for(theme in ThemeMode.entries) {
            compose.runOnIdle {mode=theme;model.navigate(AccountScreen.WELCOME)}
            compose.onAllNodesWithText("Крафтовое сообщество · 18+").assertCountEquals(1)
            compose.onNodeWithText("Создать аккаунт").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Войти").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {model.navigate(AccountScreen.VERIFY,email)}
            compose.onNodeWithText("E-mail").assertDoesNotExist()
            val message=compose.onNodeWithTag("verification-email").performScrollTo()
            message.assertTextContains(email)
            message.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(results));assertTrue(results.single().lineCount>1)
                assertFalse(results.single().hasVisualOverflow)
            }
            compose.onNodeWithText("Код из 6 цифр").performScrollTo().performTextReplacement("000123")
            compose.onNodeWithText("Подтвердить").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Отправить код снова").performScrollTo().assertIsEnabled()
        }
    }
}
