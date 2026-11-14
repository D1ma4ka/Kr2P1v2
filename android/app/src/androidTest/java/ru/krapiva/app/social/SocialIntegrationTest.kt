package ru.krapiva.app.social

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.AccountStore
import ru.krapiva.app.designsystem.KraPivaTheme
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SocialIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
    private fun request(path: String,token: String? = null,method: String = "GET",body: JSONObject? = null): JSONObject {
        val c = URL("http://10.0.2.2:53000/$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method;c.connectTimeout = 10_000;c.readTimeout = 15_000
            token?.let { c.setRequestProperty("Authorization","Bearer $it") }
            c.setRequestProperty("Idempotency-Key",UUID.randomUUID().toString())
            val payload = body ?: if(method in setOf("PUT","DELETE"))JSONObject()else null
            payload?.let { c.doOutput = true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use { stream -> stream.write(it.toString().toByteArray()) } }
            val status = c.responseCode
            assertTrue("HTTP $status $path",status in 200..299)
            val text = c.inputStream.use { String(it.readBytes()) };return if(text.isBlank())JSONObject()else JSONObject(text)
        }finally{ c.disconnect() }
    }
    private fun register(name: String): JSONObject {
        val email = "p6-${UUID.randomUUID()}@example.invalid";val password = "P6-${UUID.randomUUID()}!"
        request("v1/auth/register",method = "POST",body = JSONObject().put("email",email).put("password",password).put("displayName",name).put("adultConfirmed",true))
        val messages = JSONObject(URL("http://10.0.2.2:58025/api/v1/search?query=to:$email").readText()).getJSONArray("messages")
        val mail = JSONObject(URL("http://10.0.2.2:58025/api/v1/message/${messages.getJSONObject(0).getString("ID")}").readText())
        val code = Regex("Код подтверждения: ([0-9]{6})").find(mail.getString("Text"))!!.groupValues[1]
        request("v1/auth/verify-code",method = "POST",body = JSONObject().put("email",email).put("code",code))
        return request("v1/auth/login",method = "POST",body = JSONObject().put("email",email).put("password",password))
    }
    private fun privacy(token: String,value: String) {
        val old = request("v1/me",token)
        val fields = JSONObject().put("version",old.getInt("version")).put("profileVisibility",value).put("genderVisibility","private")
        for(key in listOf("firstName","lastName","country","city","bio","gender")) fields.put(key,JSONObject.NULL)
        request("v1/me/profile",token,"PATCH",fields)
    }
    private fun ready(model: SocialViewModel) { compose.waitUntil(20_000) { !model.state.value.busy };compose.waitForIdle();assertNull(model.state.value.error) }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    @Test fun twoRegisteredAccountsFriendshipFeedPhotosReactionsCommentsPrivacyAndOffline() {
        val fixture = JSONObject(shell("cat /data/local/tmp/p4-android.json"))
        val a = register("P6 Android A");val b = register("P6 Android B")
        val ta = a.getString("accessToken");val tb = b.getString("accessToken")
        privacy(ta,"public");privacy(tb,"public")
        val store = AccountStore(instrumentation.targetContext);store.clear()
        val api = AccountApi(store);runBlocking { api.accept(a) }
        val model = SocialViewModel(api,{ error("Expired session") })
        try {
            compose.setContent { KraPivaTheme { SocialApp(api,a.getString("userId"),{},{},{},{},providedModel = model) } }
            ready(model);compose.onNodeWithText("Лента друзей").assertExists()
            compose.onNodeWithText("Поиск").performClick();ready(model)
            compose.onNodeWithText("Имя или никнейм").performTextInput("P6 Android B");click("Найти");ready(model)
            click("Открыть профиль");ready(model);click("Добавить в друзья");ready(model)
            val pending = request("v1/friend-requests",tb).getJSONArray("items").getJSONObject(0)
            request("v1/friend-requests/${pending.getString("id")}/decision",tb,"PUT",JSONObject().put("status","accepted"))
            click("Обновить");ready(model);compose.onNodeWithText("Удалить из друзей").assertExists()
            TestEvidence.screenshot("p6-friend-profile.png")
            val bytes = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(245,166,35));compress(Bitmap.CompressFormat.PNG,100,out);recycle() } }.toByteArray()
            val c = URL("http://10.0.2.2:53000/v1/media").openConnection() as HttpURLConnection
            val photo = try { c.requestMethod = "POST";c.doOutput = true;c.setRequestProperty("Authorization","Bearer $tb");c.setRequestProperty("Content-Type","image/png");c.outputStream.use { it.write(bytes) };assertEquals(201,c.responseCode);JSONObject(c.inputStream.use { String(it.readBytes()) }).getString("id") }finally{ c.disconnect() }
            val body = JSONObject().put("positionId",fixture.getString("positionId")).put("ratingQuarters",17).put("body","P6 real shared checkin").put("occurredAt","2026-11-07T12:00:00Z").put("visibility","friends").put("mediaIds",JSONArray().put(photo))
            val checkin = request("v1/checkins",tb,"POST",body)
            compose.onNodeWithText("Лента").performClick();ready(model)
            compose.onNodeWithText("P6 real shared checkin").assertExists()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Фото в ленте").fetchSemanticsNodes().isNotEmpty() }
            TestEvidence.screenshot("p6-feed-photo.png")
            click("Лайки: 0 · комментарии: 0");ready(model)
            click("Нравится · 0");ready(model)
            assertEquals(1,request("v1/social/checkins/${checkin.getString("id")}",tb).getInt("likeCount"))
            compose.onNodeWithText("Ваш комментарий").performScrollTo().performTextInput("P6 Android comment")
            click("Отправить комментарий");ready(model)
            assertEquals("P6 Android comment",request("v1/social/checkins/${checkin.getString("id")}/comments",tb).getJSONArray("items").getJSONObject(0).getString("body"))
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("P6 Android comment"));compose.onNodeWithText("P6 Android comment").assertExists();TestEvidence.screenshot("p6-comments.png")
            click("Удалить комментарий");compose.onNodeWithText("Удалить",useUnmergedTree = true).performClick();ready(model)
            assertEquals(0,request("v1/social/checkins/${checkin.getString("id")}/comments",tb).getJSONArray("items").length())
            click("Убрать лайк · 1");ready(model)
            // Direct privacy change from second account invalidates loaded detail on refresh.
            body.remove("positionId");body.put("version",1).put("visibility","private")
            request("v1/checkins/${checkin.getString("id")}",tb,"PATCH",body)
            click("Обновить");compose.waitUntil(20_000) { !model.state.value.busy };assertNotNull(model.state.value.error);assertNull(model.state.value.data);assertTrue(model.state.value.items.isEmpty())
            compose.onNodeWithText("P6 real shared checkin").assertDoesNotExist();TestEvidence.screenshot("p6-private-unavailable.png")
            compose.onNodeWithText("Лента").performClick();ready(model);assertTrue(model.state.value.items.isEmpty())
            // No persistent social or photo cache can reveal the revoked payload.
            assertNull(store.cachedCatalog(a.getString("userId"),"v1/feed?limit=20"));assertNull(store.photo(a.getString("userId"),photo))
            // Incoming accept is also an actual Android action.
            request("v1/friends/${a.getString("userId")}",tb,"DELETE")
            request("v1/friend-requests/${UUID.randomUUID()}",tb,"PUT",JSONObject().put("userId",a.getString("userId")))
            compose.onNodeWithText("Заявки").performClick();ready(model);TestEvidence.screenshot("p6-incoming-request.png")
            click("Принять");ready(model)
            assertTrue(request("v1/users/${a.getString("userId")}/relationship",tb).getBoolean("friends"))
            compose.onNodeWithText("Друзья").performClick();ready(model);click("Удалить из друзей");compose.onNodeWithText("Удалить",useUnmergedTree = true).performClick();ready(model)
            assertFalse(request("v1/users/${a.getString("userId")}/relationship",tb).getBoolean("friends"))
            shell("svc wifi disable");shell("svc data disable")
            compose.waitUntil(15_000) { runCatching { request("health/ready");false }.getOrDefault(true) }
            compose.runOnIdle { model.refresh() };compose.waitUntil(25_000) { !model.state.value.busy };assertNotNull(model.state.value.error);assertTrue(model.state.value.items.isEmpty());TestEvidence.screenshot("p6-offline.png")
            shell("svc wifi enable");shell("svc data enable")
        } finally {
            shell("svc wifi enable");shell("svc data enable");compose.runOnIdle { model.hide() };runBlocking { api.clear() };store.close()
        }
    }
}
