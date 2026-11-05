package ru.krapiva.app.account

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.designsystem.KraPivaTheme
import java.net.URL
import java.util.UUID

/** Real local HTTP+PostgreSQL+SMTP. Never targets staging/production or external mail. */
@RunWith(AndroidJUnit4::class)
class AccountIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun androidAccountFlowAndCacheIsolation() {
        check(BuildConfig.API_ENVIRONMENT == "development" && BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AccountStore(context)
        store.clear()
        val api = AccountApi(store)
        val email = "android${UUID.randomUUID().toString().replace("-", "")}@example.invalid"
        val password = "Synthetic Android password!"
        runBlocking {
            api.json("v1/auth/register", "POST", JSONObject().put("email", email).put("password", password).put("displayName", "Synthetic Android").put("adultConfirmed", true), false)
            val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
            val message = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
            val code = Regex("Код: ([A-Za-z0-9_-]{43})").find(message.getString("Text"))!!.groupValues[1]
            api.json("v1/auth/verify", "POST", JSONObject().put("token", code), false)
        }
        compose.setContent { KraPivaTheme { AccountApp() } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Создать аккаунт").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performTextInput(email)
        compose.onNodeWithText("Пароль · 12–128 символов").performTextInput(password)
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Synthetic Android").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        compose.onNodeWithText("Город").performScrollTo().performTextReplacement("Тестовый город")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Профиль сохранён на сервере").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Тестовый город").assertExists()
        // Reconstruct the actual Android repository/store, then fetch persistent server data.
        runBlocking {
            val reopened = AccountApi(AccountStore(context))
            assertTrue(reopened.restore())
            val profile = reopened.json("v1/me")
            assertEquals("Тестовый город", profile.getString("city"))
            assertEquals("Тестовый город", reopened.cached()!!.getString("city"))
            val id = profile.getString("userId")
            assertNull(store.cached(UUID.randomUUID().toString()))
            val session = store.session()!!
            val file = java.io.File(context.noBackupFilesDir, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(file.contains(session.getString("accessToken")))
            reopened.json("v1/session", "DELETE")
            reopened.clear()
            assertNull(store.cached(id)); assertNull(store.session())
        }
        store.close()
    }
}
