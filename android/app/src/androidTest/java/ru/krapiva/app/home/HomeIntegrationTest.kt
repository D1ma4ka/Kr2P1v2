package ru.krapiva.app.home

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountScreen
import ru.krapiva.app.account.AccountViewModel
import ru.krapiva.app.designsystem.ThemeMode
import ru.krapiva.app.designsystem.ThemePreference

/** Local real API fixture, no mocked screen data. Run explicitly, outside the full P0–P6 suite. */
class HomeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun awaitNode(tag: String) = compose.waitUntil(60_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun realHomeThemesNavigationAndConfirmedLike() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p61-android.json"))
            .use { JSONObject(String(it.readBytes())) }
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(60_000) { !root.state.value.busy }
        compose.runOnIdle { root.login(fixture.getString("email"),fixture.getString("password")) }
        awaitNode("home-latest")
        lateinit var home: HomeViewModel
        compose.runOnIdle { home = ViewModelProvider(root.catalogOwner).get("home-${root.state.value.profile!!.getString("userId")}",HomeViewModel::class.java) }
        compose.waitUntil(60_000) { !home.state.value.loading }
        assertNull(home.state.value.error);assertNull(home.state.value.socialError)
        assertEquals(1,home.state.value.summary!!.getInt("totalCheckins"))
        assertEquals("1",home.state.value.friends)
        compose.onNodeWithText("Привет, Саша!").assertExists()
        // Select via the actual settings UI; screenshot both themes at identical scroll position.
        compose.onNodeWithTag("home-settings").performClick()
        compose.onNodeWithText("Тёмная").performScrollTo().performClick()
        compose.onNodeWithTag("nav-HOME").performClick()
        awaitNode("home-latest")
        compose.waitUntil(60_000) { compose.onAllNodesWithContentDescription("Солнечный лагер").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle();TestEvidence.screenshot("p61-home-dark.png")
        compose.onNodeWithTag("home-settings").performClick()
        compose.onNodeWithText("Светлая").performScrollTo().performClick()
        compose.onNodeWithTag("nav-HOME").performClick()
        awaitNode("home-latest")
        compose.waitUntil(60_000) { !home.state.value.loading }
        compose.waitUntil(60_000) { compose.onAllNodesWithContentDescription("Солнечный лагер").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle();TestEvidence.screenshot("p61-home-light.png")
        compose.activityRule.scenario.recreate()
        awaitNode("home-latest")
        assertEquals(ThemeMode.LIGHT,ThemePreference(instrumentation.targetContext).mode)
        compose.waitUntil(60_000) { !home.state.value.loading }
        val postId = home.state.value.feed!!.getString("id")
        val likedBefore = home.state.value.feed!!.getBoolean("liked")
        compose.onNodeWithTag("club-home").performScrollToNode(hasTestTag("home-like"))
        compose.onNodeWithTag("home-like").performClick()
        compose.waitUntil(60_000) { !home.state.value.likeBusy }
        assertNull(home.state.value.socialError)
        runBlocking { assertEquals(!likedBefore,root.api.json("v1/social/checkins/$postId").getBoolean("liked")) }
        // Each tab enters the existing feature, and shared navigation stays available.
        for ((tag,screen) in listOf("CATALOG" to AccountScreen.CATALOG,"COLLECTION" to AccountScreen.DIARY,"FEED" to AccountScreen.SOCIAL,"PROFILE" to AccountScreen.PROFILE)) {
            compose.onNodeWithTag("nav-$tag").performClick()
            compose.waitUntil(60_000) { root.state.value.screen == screen }
            compose.onNodeWithTag("club-navigation").assertExists()
        }
        compose.onNodeWithTag("nav-HOME").performClick()
        awaitNode("home-latest")
        compose.onNodeWithTag("club-home").performScrollToIndex(0)
        compose.onNodeWithTag("home-latest").performClick()
        compose.waitUntil(60_000) { root.state.value.screen == AccountScreen.DIARY }
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Солнечный лагер").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("detail:${fixture.getString("ownCheckinId")}",root.diaryEntry)
        compose.onNodeWithTag("nav-HOME").performClick()
        awaitNode("home-new-checkin")
        compose.onNodeWithTag("home-new-checkin").performClick()
        compose.waitUntil(60_000) { root.state.value.screen == AccountScreen.DIARY }
        assertEquals("new",root.diaryEntry)
    }
}
