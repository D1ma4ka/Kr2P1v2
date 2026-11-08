package ru.krapiva.app.diary

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountViewModel
import ru.krapiva.app.account.AccountStore

class DiaryRetentionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualRecreationRetainsEditableDraftAndLogoutClearsScope() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p4-android.json")).use { JSONObject(String(it.readBytes())) }
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(15_000) { !root.state.value.busy }
        compose.runOnIdle { root.login(fixture.getString("email"),fixture.getString("password")) }
        compose.waitUntil(20_000) { root.state.value.profile != null && !root.state.value.busy }
        val user = root.state.value.profile!!.getString("userId")
        compose.runOnIdle { root.openDiary("new",JSONObject().put("id",fixture.getString("positionId")).put("name","Synthetic Android Тёмный Ёж")) }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Заметка о пиве").fetchSemanticsNodes().isNotEmpty() }
        lateinit var diary: DiaryViewModel
        compose.runOnIdle { diary = ViewModelProvider(root.catalogOwner).get("diary-$user",DiaryViewModel::class.java) }
        compose.waitUntil(15_000) { !diary.state.value.busy }
        compose.onNodeWithText("Заметка о пиве").performTextInput("Synthetic unsaved recreation")
        compose.onNodeWithText("Оценка 0,25–5 · пусто = без оценки").performTextInput("4,75")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Заметка о пиве").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Заметка о пиве").assertTextContains("Synthetic unsaved recreation")
        compose.onNodeWithText("Оценка 0,25–5 · пусто = без оценки").assertTextContains("4,75")
        compose.runOnIdle { assertSame(root,ViewModelProvider(compose.activity)[AccountViewModel::class.java]);assertSame(diary,ViewModelProvider(root.catalogOwner).get("diary-$user",DiaryViewModel::class.java)) }
        compose.onNodeWithText("Сохранить черновик на устройстве").performScrollTo().performClick()
        compose.waitUntil(15_000) { !diary.state.value.busy }
        val store = AccountStore(instrumentation.targetContext)
        assertEquals(1,store.drafts(user).length());TestEvidence.screenshot("p5-recreated-editor.png")
        // Actual diary → catalog deep link consumes the request once, including initial loading.
        compose.runOnIdle { root.openPosition(fixture.getString("positionId")) }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Сделать чекин").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertNull(root.catalogPosition) }
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Хочу попробовать") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Хочу попробовать").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Убрать из wishlist").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val client = ru.krapiva.app.account.AccountApi(AccountStore(instrumentation.targetContext));assertTrue(client.restore())
            assertTrue(client.json("v1/me/wishlist/${fixture.getString("positionId")}").getBoolean("wished"))
        }
        compose.onNodeWithText("Убрать из wishlist").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Хочу попробовать").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сделать чекин").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Заметка о пиве").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { root.logout() }
        compose.waitUntil(20_000) { root.state.value.profile == null && !root.state.value.busy }
        assertTrue(diary.viewModelScope.coroutineContext[Job]!!.isCancelled)
        assertEquals(0,store.drafts(user).length());assertNull(store.session());store.close()
    }
}
