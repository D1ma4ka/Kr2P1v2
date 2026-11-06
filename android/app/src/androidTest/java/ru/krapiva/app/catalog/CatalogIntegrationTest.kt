package ru.krapiva.app.catalog

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.AccountStore
import ru.krapiva.app.designsystem.KraPivaTheme

/** Real HTTP/SQL, generated fixture is transferred privately by the host, never a mock API. */
@RunWith(AndroidJUnit4::class)
class CatalogIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()) }
    @Test fun catalogSearchBreweryScannerProposalAndEncryptedCache() {
        check(BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val fixture = JSONObject(shell("cat /data/local/tmp/p4-android.json"))
        val store = AccountStore(instrumentation.targetContext); store.clear()
        val api = AccountApi(store)
        runBlocking { api.accept(api.json("v1/auth/login","POST",JSONObject().put("email",fixture.getString("email")).put("password",fixture.getString("password")),false)) }
        val model = CatalogViewModel(CatalogRepository(api), { error("Not used") }, { error("Session expired") })
        compose.setContent {
            // The same production UI, repository, state and scanner implementations.
            KraPivaTheme { CatalogApp(api,store.session()!!.getString("userId"), { error("Not used") }, {}, {},model) }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Synthetic Android Тёмный Ёж").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-catalog.png")
        compose.onNodeWithText("Название, пивоварня или стиль").performTextInput("темный еж stout")
        compose.onNodeWithText("Найти").performClick()
        compose.waitUntil(15_000) { !model.state.value.busy }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Synthetic Android Тёмный Ёж").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic Android Тёмный Ёж").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Проверено по источнику").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-position.png")
        compose.onNodeWithText("Synthetic Android Brewery").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Позиции пивоварни").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-brewery.png")
        compose.onNodeWithText("Позиции пивоварни").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Сканировать штрихкод").fetchSemanticsNodes().isNotEmpty() }
        // Permission is requested only here; grant through the actual Android permission UI.
        compose.onNodeWithText("Сканировать штрихкод").performScrollTo().performClick()
        val device = UiDevice.getInstance(instrumentation)
        val allow = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button")),10_000)
        allow?.click()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Камера готова · наведите на штрихкод").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-scanner-camera.png")
        compose.onNodeWithText("Введите EAN / UPC / GTIN").performScrollTo().performTextInput(fixture.getString("gtin"))
        compose.onNodeWithText("Проверить код").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Synthetic Android Тёмный Ёж").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Назад").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Проверить код").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Введите EAN / UPC / GTIN").performScrollTo().performTextReplacement("96385074")
        compose.onNodeWithText("Проверить код").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Позиция не найдена").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-scanner-unknown.png")
        compose.onNodeWithText("Предложить позицию").performScrollTo().performClick()
        compose.onNodeWithText("Название *").performScrollTo().performTextInput("Synthetic Android proposal")
        compose.onNodeWithText("Пивоварня *").performScrollTo().performTextInput("Synthetic new brewery")
        compose.onNodeWithText("Источник сведений *").performScrollTo().performTextInput("Synthetic own-label test")
        compose.onNodeWithText("Отправить на проверку").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Ожидает проверки").fetchSemanticsNodes().isNotEmpty() }
        TestEvidence.screenshot("p4-proposals.png")
        runBlocking {
            val proposals = api.json("v1/me/position-proposals").getJSONArray("items")
            assertTrue((0 until proposals.length()).any { proposals.getJSONObject(it).getJSONObject("proposedData").getString("name") == "Synthetic Android proposal" })
            val user = store.session()!!.getString("userId")
            assertNotNull(store.cachedCatalog(user,"v1/positions/${fixture.getString("positionId")}"))
            assertNull(store.cachedCatalog(java.util.UUID.randomUUID().toString(),"v1/positions/${fixture.getString("positionId")}"))
        }
        // Actual offline read: disable the emulator radios and use a newly constructed repository.
        shell("svc wifi disable"); shell("svc data disable")
        try {
            runBlocking {
                val reopened = AccountApi(store); assertTrue(reopened.restore())
                val cached = reopened.catalogGet("v1/positions/${fixture.getString("positionId")}")
                assertTrue(cached.second); assertEquals(fixture.getString("positionId"),cached.first.getString("id"))
            }
        } finally { shell("svc wifi enable"); shell("svc data enable") }
        val owner = store.session()!!.getString("userId")
        store.clear(); assertNull(store.cachedCatalog(owner,"v1/positions/${fixture.getString("positionId")}")); store.close()
    }
}
