package ru.krapiva.app.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import ru.krapiva.app.designsystem.ThemeMode
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
    private fun choosePlace(
        kind: String, label: String, query: String, id: String,
        keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?
    ) {
        compose.onNodeWithTag("profile-$kind").performScrollTo().performClick()
        compose.onNodeWithText(label).performTextReplacement(query)
        compose.runOnIdle { keyboard?.hide() }
        compose.waitUntil(25_000) { compose.onAllNodesWithTag("place-$id").fetchSemanticsNodes().isNotEmpty() }
        if (id in setOf("RU", "524901")) screenshot("p102-$kind-search.png")
        compose.onNodeWithTag("place-$id").performClick()
    }

    @Test fun androidAccountFlowAndCacheIsolation() {
        check(BuildConfig.API_ENVIRONMENT == "development" && BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AccountStore(context)
        store.clear()
        val email = "android${UUID.randomUUID().toString().replace("-", "")}@example.invalid"
        val password = "Synthetic Android password!"
        val handle = "android_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val coreOnly = InstrumentationRegistry.getArguments().getString("krapivaCoreOnly") == "true"
        lateinit var model: AccountViewModel
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        compose.setContent { keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current; model = viewModel(); KraPivaTheme(theme, { theme = it }) { AccountApp(model) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Создать аккаунт").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p3-welcome.png")
        compose.onNodeWithText("Создать аккаунт").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performTextReplacement(email)
        compose.onNodeWithText("Никнейм").performTextInput(handle)
        compose.onNodeWithText("Пароль — от 8 символов").performTextInput(password)
        compose.onNodeWithText("Повторите пароль").performTextInput(password)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        activate("Зарегистрироваться", keyboard)
        waitForScreen(model, AccountScreen.VERIFY, 35_000)
        screenshot("p101-verify-countdown.png")
        val code = runBlocking {
            val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
            val message = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
            Regex("Код подтверждения: ([0-9]{6})").find(message.getString("Text"))!!.groupValues[1]
        }
        compose.onNodeWithText("Код из 6 цифр").performTextReplacement(code)
        // Wait for token-driven enabled semantics and native IME layout before the physical click.
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Подтвердить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        // Close IME before measuring visibility: insets settle independently of Compose.
        compose.runOnIdle { keyboard?.hide() }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        compose.waitForIdle()
        compose.onNodeWithText("Подтвердить").performScrollTo()
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        compose.onNodeWithText("Подтвердить").assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { click -> assertTrue(click()) }
        try { compose.waitUntil(20_000) { model.state.value.screen == AccountScreen.HOME && !model.state.value.busy } }
        catch(error: Throwable) { throw AssertionError("P3_VERIFY_STATE=${model.state.value.screen},busy=${model.state.value.busy},error=${model.state.value.error}",error) }
        compose.onNodeWithTag("home-settings").assertIsDisplayed()
        screenshot("p102-home-settings.png")
        compose.onNodeWithTag("home-settings").performClick()
        for (mode in ThemeMode.entries) {
            compose.runOnIdle { theme = mode }
            compose.onNodeWithText("Настройки").performScrollTo().assertIsDisplayed()
            screenshot("p102-settings-${mode.name.lowercase()}.png")
        }
        compose.onNodeWithText("Имя, аватар и информация о себе").performScrollTo().performClick()
        compose.onNodeWithText("Никнейм").assertExists()
        compose.onNodeWithText("Имя").performScrollTo().performTextReplacement("Дарья 李")
        compose.onNodeWithText("Фамилия").performScrollTo().performTextReplacement("Стрельцова")
        compose.onNodeWithTag("profile-choice-Пол").performScrollTo().assertTextContains("Не указывать").performClick()
        compose.onNodeWithText("Женский").performClick()
        compose.onNodeWithTag("profile-city").assertIsNotEnabled()
        choosePlace("country", "Поиск страны", "Рос", "RU", keyboard)
        choosePlace("city", "Поиск города", "Мос", "524901", keyboard)
        compose.onNodeWithText("Биография").performScrollTo().performTextReplacement("Крафт 🍻")
        compose.onNodeWithText("Telegram").performScrollTo().performTextReplacement("https://t.me/kra_profile")
        compose.runOnIdle { keyboard?.hide() }
        for (mode in ThemeMode.entries) {
            compose.runOnIdle { theme = mode }
            compose.onNodeWithText("Редактирование профиля").performScrollTo().assertIsDisplayed()
            screenshot("p102-profile-${mode.name.lowercase()}.png")
            compose.onNodeWithText("Untappd").performScrollTo().assertIsDisplayed()
            screenshot("p102-social-${mode.name.lowercase()}.png")
        }
        activate("Сохранить", keyboard)
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Профиль сохранён").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Moscow", substring = true).assertExists()
        screenshot("p3-profile.png")
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
        choosePlace("city", "Поиск города", "Санкт", "498817", keyboard)
        compose.onNodeWithText("Отменить изменения").performScrollTo().performClick()
        compose.onNodeWithText("Moscow", substring = true).assertExists()
        // Reconstruct the actual Android repository/store, then fetch persistent server data.
        lateinit var userId: String
        lateinit var coverId: String
        runBlocking {
            val reopened = AccountApi(AccountStore(context))
            assertTrue(reopened.restore())
            val profile = reopened.json("v1/me")
            assertEquals("Moscow", profile.getString("city"))
            assertEquals(524901, profile.getInt("cityId"))
            assertEquals("RU", profile.getString("countryId"))
            assertEquals("Дарья 李", profile.getString("firstName"))
            assertEquals("Стрельцова", profile.getString("lastName"))
            assertEquals("Крафт 🍻", profile.getString("bio"))
            assertEquals("Женский", profile.getString("gender"))
            assertEquals("https://t.me/kra_profile", profile.getJSONObject("socialLinks").getJSONObject("telegram").getString("url"))
            assertEquals("Moscow", reopened.cached()!!.getString("city"))
            userId = profile.getString("userId")
            assertNull(store.cached(UUID.randomUUID().toString()))
            val session = store.session()!!
            val file = java.io.File(context.noBackupFilesDir, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(file.contains(session.getString("accessToken")))
        }
        if (coreOnly) {
            reloginAfterPasswordReset(model, email, handle, keyboard)
            store.close()
            return
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
        waitForScreen(model, AccountScreen.PROFILE, 20_000)
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
            compose.onNodeWithText("Moscow", substring = true).assertExists()
        } finally { shell("svc data enable"); shell("svc wifi enable") }
        compose.waitUntil(30_000) { networkReady() }
        compose.onNodeWithText("Обновить").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Сохранённая копия · изменения требуют сети").fetchSemanticsNodes().isEmpty() }
        reloginAfterPasswordReset(model, email, handle, keyboard) {
            assertNull(store.cached(userId))
            assertNull(store.session())
        }
        store.close()
    }

    private fun waitForScreen(model: AccountViewModel, screen: AccountScreen, timeoutMs: Long) {
        try {
            compose.waitUntil(timeoutMs) { model.state.value.screen == screen && !model.state.value.busy }
        } catch (error: Throwable) {
            val state = model.state.value
            throw AssertionError("Expected $screen, found ${state.screen}, busy=${state.busy}, error=${state.error}", error)
        }
    }

    private fun activate(label: String, keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?) {
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
    }

    private fun reloginAfterPasswordReset(
        model: AccountViewModel, email: String, handle: String,
        keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
        afterLogout: () -> Unit = {},
    ) {
        compose.onNodeWithContentDescription("Настройки аккаунта").assertIsDisplayed().performClick()
        activate("Выйти из аккаунта", keyboard)
        waitForScreen(model, AccountScreen.LOGIN, 30_000)
        afterLogout()
        compose.onNodeWithText("Забыли пароль?").performScrollTo().performClick()
        compose.onNodeWithText("E-mail").performTextReplacement(email)
        activate("Получить код", keyboard)
        waitForScreen(model, AccountScreen.RESET, 30_000)
        val resetCode = runBlocking {
            val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
            val message = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
            Regex("Код восстановления: ([0-9]{6})").find(message.getString("Text"))!!.groupValues[1]
        }
        compose.onNodeWithText("Код из 6 цифр").performTextReplacement(resetCode)
        compose.onNodeWithText("Пароль — от 8 символов").performScrollTo().performTextInput("New synthetic Android password!")
        compose.onNodeWithText("Повторите пароль").performScrollTo().performTextInput("New synthetic Android password!")
        activate("Сменить пароль", keyboard)
        waitForScreen(model, AccountScreen.LOGIN, 30_000)
        compose.onNodeWithText("Почта или никнейм").performTextReplacement(email)
        compose.onNodeWithText("Пароль — от 8 символов").performScrollTo().performTextInput("New synthetic Android password!")
        activate("Войти", keyboard)
        waitForScreen(model, AccountScreen.HOME, 35_000)
        compose.onNodeWithTag("nav-PROFILE").performClick()
        compose.onNodeWithText("@$handle").assertExists()
        compose.onNodeWithText("Moscow", substring = true).assertExists()
        screenshot("p3-reset-login.png")
    }
}
