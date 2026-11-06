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
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import ru.krapiva.app.TestEvidence
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.regex.Pattern

/** Real local HTTP+PostgreSQL+SMTP. Never targets staging/production or external mail. */
@RunWith(AndroidJUnit4::class)
class AccountIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        TestEvidence.screenshot(name)
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun networkReady(): Boolean = runCatching {
        val connection = URL("http://10.0.2.2:53000/health/ready").openConnection() as java.net.HttpURLConnection
        try { connection.connectTimeout = 1000; connection.readTimeout = 1000; connection.responseCode == 200 }
        finally { connection.disconnect() }
    }.getOrDefault(false)
    private fun insertSyntheticPhoto(): Uri {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "p3-synthetic-${UUID.randomUUID()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KraPivaP3")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(245, 166, 35)) }
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        error("The pinned API35 acceptance image is required for the native photo picker test")
    }
    private fun pickLatestPhoto(button: String, selected: String) {
        compose.onNodeWithText(button).performScrollTo().performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val thumbnail = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/icon_thumbnail"))), 15_000)
        assertNotNull("The actual Android photo picker must show the synthetic image", thumbnail)
        thumbnail.click()
        try {
            assertTrue("Photo picker must return to the app", device.wait(Until.hasObject(By.pkg(InstrumentationRegistry.getInstrumentation().targetContext.packageName)), 15_000))
            compose.waitUntil(15_000) { compose.onAllNodesWithText(selected).fetchSemanticsNodes().isNotEmpty() }
        }
        catch (error: Throwable) {
            val currentPackage = device.currentPackageName.orEmpty()
            if (currentPackage.contains("providers.media")) TestEvidence.screenshot("p3-picker-result-timeout.png")
            throw AssertionError("Native picker result for '$button' missing; visible package=$currentPackage", error)
        }
    }
    @Test fun androidAccountFlowAndCacheIsolation() {
        check(BuildConfig.API_ENVIRONMENT == "development" && BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AccountStore(context)
        store.clear()
        val email = "android${UUID.randomUUID().toString().replace("-", "")}@example.invalid"
        val password = "Synthetic Android password!"
        lateinit var model: AccountViewModel
        compose.setContent { model = viewModel(); KraPivaTheme { AccountApp(model) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Создать аккаунт").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p3-welcome.png")
        compose.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performTextInput(email)
        compose.onNodeWithText("Отображаемое имя").performTextInput("Synthetic Android")
        compose.onNodeWithText("Пароль · 12–128 символов").performTextInput(password)
        compose.onNodeWithText("Повторите пароль").performTextInput(password)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Зарегистрироваться").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Подтверждение почты").fetchSemanticsNodes().isNotEmpty() }
        val code = runBlocking {
            val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
            val message = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
            Regex("Код: ([A-Za-z0-9_-]{43})").find(message.getString("Text"))!!.groupValues[1]
        }
        compose.onNodeWithText("Одноразовый код из письма").performTextInput(code)
        compose.onNodeWithText("Подтвердить").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Вход").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("E-mail").performTextInput(email)
        compose.onNodeWithText("Пароль · 12–128 символов").performTextInput(password)
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Synthetic Android").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        compose.onNodeWithText("Город").performScrollTo().performTextReplacement("Тестовый город")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Профиль сохранён на сервере").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Тестовый город").assertExists()
        screenshot("p3-profile.png")
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        compose.onNodeWithText("Город").performScrollTo().performTextReplacement("Несохранённый город")
        compose.onNodeWithText("Отменить изменения").performScrollTo().performClick()
        compose.onNodeWithText("Тестовый город").assertExists()
        // Reconstruct the actual Android repository/store, then fetch persistent server data.
        lateinit var userId: String
        lateinit var coverId: String
        runBlocking {
            val reopened = AccountApi(AccountStore(context))
            assertTrue(reopened.restore())
            val profile = reopened.json("v1/me")
            assertEquals("Тестовый город", profile.getString("city"))
            assertEquals("Тестовый город", reopened.cached()!!.getString("city"))
            userId = profile.getString("userId")
            assertNull(store.cached(UUID.randomUUID().toString()))
            val session = store.session()!!
            val file = java.io.File(context.noBackupFilesDir, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(file.contains(session.getString("accessToken")))
        }
        insertSyntheticPhoto()
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать аватар").performScrollTo().performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Wait for the native picker before cancelling it", device.wait(Until.hasObject(By.res(Pattern.compile(".*:id/icon_thumbnail"))), 15_000))
        device.pressBack()
        assertTrue("Cancelled picker must return to the app", device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Фото не выбрано. Профиль не изменён.").fetchSemanticsNodes().isNotEmpty() }
        pickLatestPhoto("Выбрать аватар", "Другой аватар · фото выбрано")
        pickLatestPhoto("Выбрать обложку", "Другая обложка · фото выбрано")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Аватар профиля").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Обложка профиля").assertExists()
        runBlocking {
            val reopened = AccountApi(AccountStore(context)); assertTrue(reopened.restore())
            val saved = reopened.json("v1/me")
            coverId = saved.getString("coverAssetId")
            assertNotEquals(saved.getString("avatarAssetId"), saved.getString("coverAssetId"))
            val thumbnail = reopened.photo(saved.getString("avatarAssetId"))!!
            assertEquals(32, android.graphics.BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size).width)
            assertNotNull(store.photo(userId, saved.getString("avatarAssetId")))
        }
        compose.onNodeWithContentDescription("Аватар профиля").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Фото профиля").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Закрыть").performClick()
        screenshot("p3-profile-photos.png")
        // A second real DELETE path: removing only the cover keeps the avatar
        // and makes the old media unavailable on the server after Save.
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        compose.onNodeWithText("Удалить обложку").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(20_000) { model.state.value.screen == AccountScreen.PROFILE && !model.state.value.busy }
        compose.onNodeWithContentDescription("Аватар профиля").assertExists()
        compose.onNodeWithContentDescription("Обложка профиля").assertDoesNotExist()
        runBlocking {
            val reopened = AccountApi(AccountStore(context)); assertTrue(reopened.restore())
            assertTrue(reopened.json("v1/me").isNull("coverAssetId"))
            val missing = runCatching { reopened.bytes("v1/media/$coverId") }.exceptionOrNull()
            assertTrue("Removed cover is deleted by the real API", missing is ApiFailure && missing.status == 404)
        }
        try {
            shell("svc wifi disable"); shell("svc data disable")
            compose.waitUntil(20_000) { !networkReady() }
            compose.onNodeWithText("Обновить").performScrollTo().performClick()
            compose.waitUntil(40_000) { compose.onAllNodesWithText("Нет связи. Показана сохранённая копия профиля.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Тестовый город").assertExists()
        } finally { shell("svc data enable"); shell("svc wifi enable") }
        compose.waitUntil(30_000) { networkReady() }
        compose.onNodeWithText("Обновить").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Сохранённая копия · изменения требуют сети").fetchSemanticsNodes().isEmpty() }
        // The profile arrives before the thumbnail requests finish. Navigation stays
        // disabled until that real request completes; wait for its enabled state.
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Настройки аккаунта") and isEnabled()).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Настройки аккаунта").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Выйти из аккаунта") and isEnabled()).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        try { compose.waitUntil(20_000) { compose.onAllNodesWithText("Вы вышли из аккаунта").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            TestEvidence.screenshot("p3-logout-timeout.png")
            val state = model.state.value
            throw AssertionError("Logout did not complete: screen=${state.screen}, busy=${state.busy}, error=${state.error}, notice=${state.notice}", error)
        }
        assertNull(store.cached(userId)); assertNull(store.session())
        compose.onNodeWithText("Забыли пароль?").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performTextInput(email)
        compose.onNodeWithText("Получить код").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Новый пароль").fetchSemanticsNodes().isNotEmpty() }
        val resetCode = runBlocking {
            val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
            val message = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
            Regex("Код: ([A-Za-z0-9_-]{43})").find(message.getString("Text"))!!.groupValues[1]
        }
        compose.onNodeWithText("Одноразовый код из письма").performTextInput(resetCode)
        compose.onNodeWithText("Пароль · 12–128 символов").performTextInput("New synthetic Android password!")
        compose.onNodeWithText("Повторите пароль").performTextInput("New synthetic Android password!")
        compose.onNodeWithText("Сменить пароль").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Вход").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("E-mail").performTextInput(email)
        compose.onNodeWithText("Пароль · 12–128 символов").performTextInput("New synthetic Android password!")
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        compose.waitUntil(20_000) { model.state.value.screen == AccountScreen.PROFILE && !model.state.value.busy }
        compose.onNodeWithText("Synthetic Android").assertExists()
        compose.onNodeWithText("Тестовый город").assertExists()
        screenshot("p3-reset-login.png")
        store.close()
    }
}
