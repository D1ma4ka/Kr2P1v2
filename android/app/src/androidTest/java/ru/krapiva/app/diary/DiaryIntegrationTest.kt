package ru.krapiva.app.diary

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.AccountStore
import ru.krapiva.app.designsystem.KraPivaTheme
import java.util.UUID
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class DiaryIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
    private fun waitForText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun ready(model: DiaryViewModel) { compose.waitUntil(20_000) { !model.state.value.busy };compose.waitForIdle();assertNull(model.state.value.error) }
    private fun shot(name: String) { compose.waitForIdle();UiDevice.getInstance(instrumentation).waitForIdle();TestEvidence.screenshot(name) }
    private fun networkReady(): Boolean = runCatching { val c = java.net.URL("http://10.0.2.2:53000/health/ready").openConnection() as java.net.HttpURLConnection;try { c.connectTimeout = 1000;c.readTimeout = 1000;c.responseCode == 200 }finally { c.disconnect() } }.getOrDefault(false)
    @Test fun realDiaryEditingChartsWishlistPhotosDraftsAndOfflineCache() {
        val fixture = JSONObject(shell("cat /data/local/tmp/p4-android.json"))
        val context = instrumentation.targetContext
        val store = AccountStore(context);store.clear();val api = AccountApi(store)
        runBlocking { api.accept(api.json("v1/auth/login","POST",JSONObject().put("email",fixture.getString("email")).put("password",fixture.getString("password")),false)) }
        val ownerId = store.session()!!.getString("userId")
        val model = DiaryViewModel(api,{ uri -> runBlocking { val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }; api.jsonBytesForTest(bytes) } },{ error("Session expired") })
        try {
            compose.setContent { KraPivaTheme { DiaryApp(api,store.session()!!.getString("userId"),1,"HOME",null,{ error("Supplied model") },{},{},{},{},model) } }
            ready(model);waitForText("Попробованное");compose.onNodeWithText("Всего чекинов: 0").assertExists();shot("p5-home-empty.png")
            // Open the actual editor through the home CTA and actual catalog selection.
            compose.onNodeWithText("Чекин").performClick();ready(model)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Synthetic Android Тёмный Ёж"))
            compose.onNodeWithText("Synthetic Android Тёмный Ёж").performClick();ready(model)
            compose.onNodeWithText("Оценка 0,25–5 · пусто = без оценки").performTextInput("4,25")
            compose.onNodeWithText("Бутылка").performScrollTo().performClick()
            compose.onNodeWithText("Заметка о пиве").performTextInput("Synthetic diary цитрус")
            // Real native photo picker: cancel and select a generated own image.
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"p5-synthetic.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/KraPivaP5");put(MediaStore.Images.Media.IS_PENDING,1) }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
            context.contentResolver.openOutputStream(uri)!!.use { out -> Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(245,166,35));compress(Bitmap.CompressFormat.PNG,100,out);recycle() } }
            context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            compose.onNodeWithText("Добавить фото").performScrollTo().performClick();compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
            val device = UiDevice.getInstance(instrumentation)
            assertTrue(device.wait(Until.hasObject(By.res(Pattern.compile(".*:id/icon_thumbnail"))),15_000));device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)),15_000));compose.waitForIdle()
            compose.onNodeWithText("Добавить фото").performScrollTo().performClick();compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.res(Pattern.compile(".*:id/icon_thumbnail"))),15_000));device.findObject(By.res(Pattern.compile(".*:id/icon_thumbnail")))!!.click()
            val add = device.wait(Until.findObject(By.text(Pattern.compile("(?i).*(add|добавить).*"))),3000)
            add?.click()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)),15_000));compose.waitForIdle()
            compose.onNodeWithText("1 из 4").assertExists()
            compose.onNodeWithText("Проверить пиво и сохранить").performScrollTo().performClick();ready(model)
            compose.onNode(hasText("Это другое пиво") or hasText("Подтвердить проверку")).performClick()
            compose.waitUntil(20_000) { model.state.value.page == DiaryPage.DETAIL && !model.state.value.busy }
            compose.onNodeWithTag("checkin-success").assertExists()
            compose.onNodeWithText("Посмотреть чекин").performScrollTo().performClick()
            val first = model.state.value.data!!;assertEquals(17,first.getInt("ratingQuarters"));assertEquals("Бутылка",first.getString("servingFormat"));assertEquals(1,first.getJSONArray("mediaIds").length());compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Фото чекина").fetchSemanticsNodes().isNotEmpty() }
            shot("p5-checkin-photo.png")
            compose.onNodeWithContentDescription("Фото чекина").performScrollTo().performClick();compose.onNodeWithText("Фото чекина").assertExists();shot("p5-photo-view.png");compose.onNodeWithText("Закрыть").performClick()
            // A second real client writes A/B/C; refresh on Android must observe the same rows.
            runBlocking {
                val secondClient = AccountApi(AccountStore(context));assertTrue(secondClient.restore())
                for((position,rating) in listOf(fixture.getString("positionId") to 19,fixture.getString("secondPositionId") to null,fixture.getString("thirdPositionId") to 17)) secondClient.json("v1/checkins","POST",JSONObject().put("positionId",position).put("ratingQuarters",rating ?: JSONObject.NULL).put("body","Synthetic repeated checkin").put("servingFormat","Кран").put("occurredAt","2026-11-07T12:00:00Z").put("visibility","private"),requestId = UUID.randomUUID().toString())
            }
            compose.runOnIdle { model.open(DiaryPage.HOME) };ready(model);compose.onNodeWithText("Всего чекинов: 4").assertExists();compose.onNodeWithText("Уникальных позиций: 3").assertExists();shot("p5-home-counts.png")
            compose.runOnIdle { model.open(DiaryPage.STATISTICS) };ready(model);assertEquals("53",model.state.value.statistics!!.getString("ratingSumQuarters"));compose.onNodeWithText("Средняя оценка: 4,42").assertExists();shot("p5-style-statistics.png")
            compose.onNodeWithText("4,25: 2").performScrollTo();shot("p5-rating-histogram.png");compose.onNodeWithText("4,25: 2").performClick();ready(model);assertEquals(2,model.state.value.items.size)
            compose.runOnIdle { model.open(DiaryPage.STATISTICS) };ready(model)
            compose.onNodeWithText("Synthetic Android Stout: 2 · 66,67%").performScrollTo().performClick();ready(model);assertEquals(2,model.state.value.items.size);shot("p5-unique-drilldown.png")
            compose.runOnIdle { model.open(DiaryPage.DIARY,DiaryFilters(q = "цитрус",photo = "true")) };ready(model);assertEquals(1,model.state.value.items.size)
            compose.runOnIdle { model.detail(first.getString("id")) };ready(model)
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("Редактировать чекин") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Редактировать чекин").performScrollTo().performClick();compose.waitUntil(20_000) { model.state.value.page == DiaryPage.EDITOR && !model.state.value.busy };ready(model)
            compose.onNodeWithText("Оценка 0,25–5 · пусто = без оценки").performTextReplacement("5")
            compose.onNodeWithText("Убрать фото из чекина").performScrollTo().performClick()
            compose.onNodeWithText("Сохранить изменения").performScrollTo().performClick();ready(model);assertEquals(first.getString("positionId"),model.state.value.data!!.getString("positionId"));assertEquals(20,model.state.value.data!!.getInt("ratingQuarters"));assertEquals(0,model.state.value.data!!.getJSONArray("mediaIds").length())
            // Wishlist across two API instances and removal.
            runBlocking { api.json("v1/me/wishlist/${fixture.getString("secondPositionId")}","PUT");api.invalidateDiary() }
            compose.runOnIdle { model.open(DiaryPage.WISHLIST) };ready(model);assertEquals(1,model.state.value.items.size);shot("p5-wishlist.png")
            // Cached diary remains available through a real radio transition.
            compose.runOnIdle { model.open(DiaryPage.DIARY) };ready(model)
            shell("svc wifi disable");shell("svc data disable");compose.waitUntil(15_000) { !networkReady() }
            compose.runOnIdle { model.refresh() };ready(model);assertTrue(model.state.value.offline);shot("p5-diary-offline.png")
            val pos = JSONObject().put("id",fixture.getString("positionId")).put("name","Synthetic Android Тёмный Ёж")
            compose.runOnIdle { model.new(pos) };ready(model)
            compose.onNodeWithText("Заметка о пиве").performTextInput("Synthetic unsent local draft")
            compose.onNodeWithText("Сохранить черновик на устройстве").performScrollTo().performClick();ready(model)
            val drafts = runBlocking { AccountApi(AccountStore(context)).also { assertTrue(it.restore()) }.drafts() };assertEquals(1,drafts.length());assertEquals("Synthetic unsent local draft",drafts.getJSONObject(0).getString("body"))
            compose.runOnIdle { model.open(DiaryPage.DRAFTS) };ready(model);shot("p5-unsent-draft.png")
            compose.onNodeWithText("Продолжить черновик").performScrollTo().performClick();ready(model)
            compose.onNodeWithText("Заметка о пиве").assertTextContains("Synthetic unsent local draft")
            shell("svc wifi enable");shell("svc data enable");compose.waitUntil(20_000) { networkReady() }
            compose.runOnIdle { model.open(DiaryPage.DIARY) };ready(model);assertFalse(model.state.value.offline);assertEquals(4,model.state.value.items.size)
            compose.runOnIdle { model.detail(first.getString("id")) };ready(model)
            compose.onNodeWithText("Удалить чекин").performScrollTo().performClick();compose.onNodeWithText("Удалить",useUnmergedTree = true).performClick();ready(model)
            compose.runOnIdle { model.open(DiaryPage.HOME) };ready(model);assertEquals(3,model.state.value.statistics!!.getInt("totalCheckins"));assertEquals(3,model.state.value.statistics!!.getInt("uniquePositions"))
            runBlocking { api.clear() };assertEquals(0,store.drafts(ownerId).length())
        } catch(e: Throwable) {
            runCatching { shot("p5-failure.png") }
            throw AssertionError("P5_STATE=${model.state.value.page},busy=${model.state.value.busy},error=${model.state.value.error},apiStatus=${(e as? ru.krapiva.app.account.ApiFailure)?.status}\n$e",e)
        } finally { shell("svc wifi enable");shell("svc data enable");store.close() }
    }
    private suspend fun AccountApi.jsonBytesForTest(bytes: ByteArray): String = JSONObject(String(bytes("v1/media","POST",bytes,"image/png"))).getString("id")
}
