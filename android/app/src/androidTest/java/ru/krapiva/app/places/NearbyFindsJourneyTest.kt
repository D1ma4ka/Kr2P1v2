package ru.krapiva.app.places

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
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.mapview.MapView
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

    private fun login(): AccountViewModel {
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(60_000) { !root.state.value.busy }
        if (root.state.value.profile != null) {
            compose.runOnIdle { root.logout() }
            compose.waitUntil(60_000) { !root.state.value.busy }
        }
        compose.runOnIdle { root.login("beta@example.invalid", "Isolated Smart Bar QA!") }
        compose.waitUntil(60_000) { root.state.value.profile != null && !root.state.value.busy }
        return root
    }

    /** Run with precise foreground permission and Seattle supplied through the emulator console. */
    @Test fun homeIgnoresEmulatorSeattleAndOpensPublishedMenuInChosenCity() {
        assertTrue(emulatedDevice && hasLocationPermission(compose.activity))
        val emulatorFix = requireNotNull(runBlocking { withTimeout(15_000) { currentLocation(compose.activity) } })
        assertEquals(47.6062, emulatorFix.latitude, 0.05)
        assertEquals(-122.3321, emulatorFix.longitude, 0.05)
        val root = login()
        awaitText("Твой никнейм")
        compose.onNodeWithText("Позже").performClick()
        compose.onNodeWithText("Твой никнейм").assertDoesNotExist()
        val home = compose.onNodeWithTag("club-home")
        val chooseOrigin = "Открой карту, чтобы выбрать город или использовать геопозицию."
        home.performScrollToNode(hasText(chooseOrigin))
        compose.onNodeWithText(chooseOrigin).assertExists()
        assertNull(root.discoveryCity)

        home.performScrollToNode(hasText("Открыть карту"))
        compose.onNodeWithText("Открыть карту").performClick()
        awaitText("Выбрать город")
        compose.onNodeWithText("Карта").assertIsNotEnabled()
        TestEvidence.screenshot("p103-places-no-origin.png")
        compose.onNodeWithText("Выбрать город").performClick()
        awaitText("QA · 2")
        compose.onNodeWithText("QA · 2").performClick()
        compose.waitUntil(30_000) { root.discoveryCity?.name == "QA" }
        compose.onNodeWithTag("nav-HOME").performClick()
        home.performScrollToNode(hasText("Места рядом"))
        awaitText("QA · 5 км")
        home.performScrollToNode(hasText("A") and hasClickAction())
        TestEvidence.screenshot("p103-home-chosen-city.png")
        compose.onNode(hasText("A") and hasClickAction()).performClick()
        awaitText("Меню")
        compose.onNodeWithText("Меню").performScrollTo().performClick()
        val offer = hasText("Тестовое длинное название", substring = true)
        compose.waitUntil(45_000) { compose.onAllNodes(offer).fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p103-published-menu.png")
    }

    @Test fun cityWithoutLocationMapListSavedFilterReturnAndExistingCheckin() {
        val automation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(automation)
        val arguments = InstrumentationRegistry.getArguments()
        val captureScreenshots = arguments.getString("captureScreenshots") != "false"
        val root = login()
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
        if (mapConfigured) {
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("find-map").fetchSemanticsNodes().isNotEmpty() }
            val point = Point(55.7, 37.6)
            val native = compose.runOnIdle { requireNotNull(nativeView(compose.activity.window.decorView)) }
            compose.runOnIdle { native.mapWindow.map.move(CameraPosition(point, 20f, 0f, 0f)) }
            val tap = compose.runOnIdle { requireNotNull(native.mapWindow.worldToScreen(point)) }
            compose.onNodeWithTag("find-map").performTouchInput { click(androidx.compose.ui.geometry.Offset(tap.x, tap.y)) }
            awaitText("Подходящих позиций · 48")
            if (captureScreenshots) TestEvidence.screenshot("p87-find-map.png")
        } else {
            awaitText(missingMapKey)
            compose.onNodeWithTag("find-list").assertExists()
        }
        compose.onNodeWithText("Список").performClick()
        compose.onNodeWithTag("find-list").assertExists()
    }
}
