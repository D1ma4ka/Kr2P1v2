package ru.krapiva.app.account

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.designsystem.*
import org.json.JSONObject
import java.net.URL
import java.util.UUID

class AuthFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothThemesKeepAuthAccessibleWithoutTechnicalMessages() {
        val store = AccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear(); store.close()
        lateinit var model: AccountViewModel
        var mode by mutableStateOf(ThemeMode.DARK)
        compose.setContent { model = viewModel(); KraPivaTheme(mode) { AccountApp(model) } }
        compose.waitUntil(15_000) { model.state.value.screen == AccountScreen.WELCOME }
        for (theme in ThemeMode.entries) {
            compose.runOnIdle { mode = theme; model.navigate(AccountScreen.REGISTER) }
            compose.onNodeWithText("Пароль — от 8 символов").performScrollTo().assertIsDisplayed()
            assertNoDevelopmentMessages()
            compose.onNodeWithTag("auth-logo").assertExists()
            compose.onNodeWithTag("auth-beer").assertDoesNotExist()
            TestEvidence.screenshot("auth-register-${theme.name.lowercase()}.png")
            compose.onNodeWithText("Зарегистрироваться").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { model.navigate(AccountScreen.LOGIN) }
            assertNoDevelopmentMessages()
            compose.onNodeWithTag("auth-logo").assertExists()
            compose.onNodeWithTag("auth-beer").assertDoesNotExist()
            compose.onNodeWithText("Ответственное потребление", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Войти").performScrollTo().assertIsDisplayed()
        }
    }

    private fun assertNoDevelopmentMessages() {
        for (text in listOf("Среда:", "Environment:", "development", "example.invalid", "Mailpit", "Локальная почта")) {
            compose.onNodeWithText(text, substring = true).assertDoesNotExist()
        }
    }

    @Test fun registrationUsesMailpitSixDigitsPreservesEmailAndOpensHome() {
        check(BuildConfig.API_ENVIRONMENT == "development")
        val host = URL(BuildConfig.API_BASE_URL).host
        check(host in setOf("127.0.0.1", "localhost", "10.0.2.2"))
        val store = AccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear(); store.close()
        val email = "otp-ui-${UUID.randomUUID()}@example.invalid"
        val password = "Synthetic UI OTP password!"
        lateinit var model: AccountViewModel
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        compose.setContent {
            keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            model = viewModel(); KraPivaTheme { AccountApp(model) }
        }
        compose.waitUntil(15_000) { model.state.value.screen == AccountScreen.WELCOME }
        compose.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        compose.onNodeWithText("Зарегистрироваться").assertIsNotEnabled()
        compose.onNodeWithText("E-mail").performTextReplacement(email)
        compose.onNodeWithText("Никнейм").performTextInput("otp_ui_" + UUID.randomUUID().toString().replace("-", "").take(12))
        compose.onNodeWithText("Пароль — от 8 символов").performScrollTo().performTextInput(password)
        compose.onNodeWithText("Повторите пароль").performScrollTo().performTextInput(password)
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("Зарегистрироваться").assertIsNotEnabled()
        compose.onNodeWithText("Подтверждаю, что мне исполнилось 18 лет").performScrollTo().performClick()
        compose.onNodeWithText("Повторите пароль").performScrollTo().performTextReplacement("Different password!")
        compose.runOnIdle { keyboard?.hide() }
        repeat(2) { compose.onNodeWithText("Зарегистрироваться").performScrollTo().assertIsEnabled().performClick() }
        compose.onNodeWithText("Пароли не совпадают").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("E-mail").assertTextContains(email)
        compose.onNodeWithText("Пароль — от 8 символов").assertTextContains(password)
        compose.onNodeWithText("Повторите пароль").assertTextContains("Different password!")
        compose.runOnIdle { assertEquals(AccountScreen.REGISTER, model.state.value.screen); assertFalse(model.state.value.busy) }
        compose.onNodeWithText("Повторите пароль").performScrollTo().performTextReplacement(password)
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("Зарегистрироваться").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(20_000) { model.state.value.screen == AccountScreen.VERIFY && !model.state.value.busy }
        compose.onNodeWithTag("verification-email").assertTextContains(email)
        compose.onNodeWithText("E-mail").assertDoesNotExist()
        val mailbox = "http://$host:58025"
        val messages = JSONObject(URL("$mailbox/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
        val message = JSONObject(URL("$mailbox/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText()).getString("Text")
        assertFalse(message.contains("Код:"))
        val code = checkNotNull(Regex("Код подтверждения: ([0-9]{6})").find(message)).groupValues[1]
        compose.onNodeWithText("Код из 6 цифр").performTextReplacement(code)
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("Подтвердить").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        compose.waitUntil(20_000) { model.state.value.screen == AccountScreen.LOGIN && !model.state.value.busy }
        compose.onNodeWithText("Почта или никнейм").assertTextContains(email)
        compose.onNodeWithText("Пароль — от 8 символов").performScrollTo().performTextInput(password)
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.waitUntil(30_000) { model.state.value.screen == AccountScreen.HOME && !model.state.value.busy }
        compose.onNodeWithTag("club-navigation").assertExists()
        assertNotNull(model.state.value.profile)
    }

    @Test fun otpKeepsLeadingZeroesRejectsPartialAndLimitsPaste() {
        val store = AccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear(); store.close()
        lateinit var model: AccountViewModel
        compose.setContent { model = viewModel(); KraPivaTheme { AccountApp(model) } }
        compose.waitUntil(15_000) { model.state.value.screen == AccountScreen.WELCOME }
        compose.runOnIdle { model.navigate(AccountScreen.VERIFY, "ui@example.invalid") }
        compose.onNodeWithText("E-mail").assertDoesNotExist()
        compose.onNodeWithText("Код из 6 цифр").performTextReplacement("00012")
        compose.onNodeWithText("Подтвердить").assertIsNotEnabled()
        compose.onNodeWithText("Код из 6 цифр").performTextReplacement(" 00-12 34 567")
        compose.onNodeWithText("Код из 6 цифр").assertTextContains("001234")
        compose.onNodeWithText("Подтвердить").assertIsEnabled()
    }
}
