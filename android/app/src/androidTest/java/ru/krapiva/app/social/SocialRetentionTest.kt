package ru.krapiva.app.social

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

class SocialRetentionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun directEntryRecreationRetainsCommentAndLogoutClearsAccountScope() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p4-android.json")).use { JSONObject(String(it.readBytes())) }
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(15_000) { !root.state.value.busy }
        compose.runOnIdle { root.login(fixture.getString("email"),fixture.getString("password")) }
        compose.waitUntil(20_000) { root.state.value.profile != null && !root.state.value.busy }
        val user = root.state.value.profile!!.getString("userId")
        val id = runBlocking { root.api.json("v1/checkins","POST",JSONObject().put("positionId",fixture.getString("positionId")).put("ratingQuarters",17).put("body","P6 recreation fixture").put("occurredAt","2026-11-07T12:00:00Z").put("visibility","private"),requestId = java.util.UUID.randomUUID().toString()).getString("id") }
        try {
            compose.runOnIdle { root.openSocial(id) }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Обсуждение чекина").fetchSemanticsNodes().isNotEmpty() }
            lateinit var model: SocialViewModel
            compose.runOnIdle { model = ViewModelProvider(root.catalogOwner).get("social-$user",SocialViewModel::class.java) }
            compose.waitUntil(15_000) { !model.state.value.busy && model.state.value.data != null }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ваш комментарий"))
            compose.onNodeWithText("Ваш комментарий").performScrollTo().performTextInput("P6 unsent retained comment")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.data != null }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ваш комментарий"))
            compose.onNodeWithText("Ваш комментарий").assertTextContains("P6 unsent retained comment")
            compose.runOnIdle {
                assertSame(root,ViewModelProvider(compose.activity)[AccountViewModel::class.java])
                assertSame(model,ViewModelProvider(root.catalogOwner).get("social-$user",SocialViewModel::class.java))
                assertEquals(SocialPage.DETAIL,model.state.value.page);assertEquals(id,model.state.value.target)
            }
            TestEvidence.screenshot("p6-recreated-comment.png")
            // A new explicit entry to the same checkin must start a new comment draft.
            compose.runOnIdle { root.openSocial(id) }
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.comment.isEmpty() }
            runBlocking { root.api.json("v1/checkins/$id","DELETE",JSONObject().put("version",1)) }
            compose.runOnIdle { root.logout() }
            compose.waitUntil(20_000) { root.state.value.profile == null && !root.state.value.busy }
            compose.waitForIdle()
            assertTrue(model.viewModelScope.coroutineContext[Job]!!.isCancelled)
            assertNull(model.state.value.data);assertTrue(model.state.value.items.isEmpty())
        } finally {
            if(root.state.value.profile != null) runBlocking { root.api.json("v1/checkins/$id","DELETE",JSONObject().put("version",1)) }
        }
    }
}
