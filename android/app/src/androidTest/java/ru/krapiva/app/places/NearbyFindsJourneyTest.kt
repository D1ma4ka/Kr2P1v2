package ru.krapiva.app.places

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountScreen
import ru.krapiva.app.account.AccountViewModel
import ru.krapiva.app.menu.MenuFilter
import ru.krapiva.app.menu.MenuPresetRepository
import ru.krapiva.app.menu.MenuRange
import ru.krapiva.app.menu.Tried
import java.time.Instant
import java.util.regex.Pattern

/** Uses the isolated PostgreSQL Smart Bar stand on the debug client's configured URL. */
class NearbyFindsJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(60_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun nativeView(view: View): MapView? = when (view) {
        is MapView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { nativeView(view.getChildAt(it)) }
        else -> null
    }

    @Test fun cityWithoutLocationMapListSavedFilterReturnAndExistingCheckin() {
        val automation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(automation)
        val arguments = InstrumentationRegistry.getArguments()
        val captureScreenshots = arguments.getString("captureScreenshots") != "false"
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(60_000) { !root.state.value.busy }
        if (root.state.value.profile != null) {
            compose.runOnIdle { root.logout() }
            compose.waitUntil(60_000) { !root.state.value.busy }
        }
        compose.runOnIdle { root.login("beta@example.invalid", "Isolated Smart Bar QA!") }
        compose.waitUntil(60_000) { root.state.value.profile != null && !root.state.value.busy }
        val rule = MenuFilter(styles = setOf("ipa", "apa"), tried = Tried.NO,
            abv = MenuRange("4", "8"), volumeMl = MenuRange("250", "330"))
        runBlocking {
            MenuPresetRepository(root.api, checkNotNull(root.api.ownerId())).edit {
                it.save("nearby-device-ipa", "Мои IPA", rule, Instant.now())
            }
        }
        compose.runOnIdle { root.openVenues() }
        compose.onNodeWithTag("nearby-finds").performClick()
        compose.onNodeWithText("Список").performClick()
        compose.onNodeWithTag("find-conditions").performClick()
        assertFalse("Use a test install without location permission", hasLocationPermission(compose.activity))
        if (arguments.getString("locationDialog") == "true") {
            compose.onNodeWithText("Рядом со мной").performClick()
            val permission = device.wait(
                Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 60_000,
            )
            assertNotNull("Android location permission", permission)
            permission!!.click()
            awaitText("Геопозиция отключена. Можно выбрать город или точку на карте.")
            compose.onNodeWithTag("find-conditions").performClick()
        }
        compose.onNodeWithText("Выбрать город").performClick()
        awaitText("QA")
        compose.onNodeWithText("QA").performClick()
        compose.onNodeWithTag("find-radius").performClick()
        compose.onAllNodesWithText("3 км", substring = true).filter(hasClickAction()).onLast().performClick()
        compose.onNodeWithTag("find-conditions").performClick()
        compose.onAllNodesWithText("Ещё не пробовал").filterToOne(hasClickAction()).performClick()
        compose.onNodeWithText("Мой фильтр").performClick()
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithText("Мои IPA").performClick()
        compose.onNodeWithText("Ближе к точке поиска").performClick()
        compose.onNodeWithText("Больше совпадений").performClick()
        compose.onNodeWithText("Применить").performClick()
        awaitText("Подходящих позиций · 48")
        compose.onNodeWithTag("find-list").performScrollToNode(hasText("Подходящих позиций · 5"))
        if (captureScreenshots) TestEvidence.screenshot("p87-find-list.png")

        val list = compose.onNodeWithTag("find-list")
        list.performScrollToIndex(0)
        compose.onAllNodesWithText("Посмотреть находки").onFirst().performClick()
        awaitText("Находки · A")
        compose.activityRule.scenario.recreate()
        awaitText("Находки · A")
        awaitText("Советник Smart Bar")
        val menu = compose.onNodeWithTag("find-menu")
        menu.performScrollToNode(hasText("Чекин"))
        compose.onAllNodesWithText("Чекин").onFirst().performClick()
        compose.waitUntil(60_000) { root.state.value.screen == AccountScreen.DIARY && root.diaryPosition != null }
        assertTrue(root.diaryReturnsToMenu)
        assertEquals("A", root.diaryVenue!!.getString("name"))
        assertTrue(root.diaryPosition!!.getString("styleName") in setOf("IPA", "APA"))
        compose.runOnIdle { root.returnToMenu() }
        awaitText("Находки · A")
        compose.onNodeWithText("К находкам").performClick()
        awaitText("QA · Мои IPA")
        compose.onNodeWithTag("find-radius").assert(hasText("3 км"))

        compose.onNodeWithText("Карта").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("find-map").fetchSemanticsNodes().isNotEmpty() }
        var native: MapLibreMap? = null
        compose.runOnIdle {
            requireNotNull(nativeView(compose.activity.window.decorView)).getMapAsync { map ->
                native = map
                map.cameraPosition = CameraPosition.Builder().target(LatLng(55.7, 37.6)).zoom(19.0).build()
            }
        }
        compose.waitUntil(60_000) {
            compose.runOnIdle {
                native?.queryRenderedFeatures(RectF(0f, 0f, 10000f, 10000f), "points")?.any {
                    it.getNumberProperty("match_count")?.toInt() == 48
                } == true
            }
        }
        if (captureScreenshots) TestEvidence.screenshot("p87-find-map.png")
        compose.onNodeWithText("Список").performClick()
        compose.onNodeWithTag("find-list").assertExists()
    }
}
