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
    private fun networkReady(): Boolean = runCatching {
        val connection = java.net.URL("http://10.0.2.2:53000/health/ready").openConnection() as java.net.HttpURLConnection
        try { connection.connectTimeout = 1000; connection.readTimeout = 1000; connection.responseCode == 200 }
        finally { connection.disconnect() }
    }.getOrDefault(false)
    private fun waitForText(value: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { runCatching { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).waitForIdle()
        TestEvidence.screenshot(name)
    }
    private fun clickPermissionButton(resource: String) {
        // Use the real native button's accessibility action. Coordinate injection can
        // hit the underlying manual form while the permission window is animating.
        var button: android.view.accessibility.AccessibilityNodeInfo? = null
        compose.waitUntil(10_000) {
            button = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId(resource)?.firstOrNull { it.isClickable && it.isVisibleToUser }
            button != null
        }
        assertTrue("Native permission button must handle click",button!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }
    private var activeModel: CatalogViewModel? = null
    @Test fun catalogSearchBreweryScannerProposalAndEncryptedCache() {
        try { catalogFlow() } catch (error: Throwable) {
            val device = UiDevice.getInstance(instrumentation)
            println("P4_STATE=" + activeModel?.state?.value?.let { "page=${it.page},busy=${it.busy},error=${it.error},lookup=${it.lookupState}" })
            println("P4_NATIVE_PACKAGE=" + device.currentPackageName)
            println("P4_CAMERA_PERMISSION=" + instrumentation.targetContext.checkSelfPermission(android.Manifest.permission.CAMERA))
            runCatching { val out = java.io.ByteArrayOutputStream(); device.dumpWindowHierarchy(out); println("P4_NATIVE_UI=" + out.toString("UTF-8").take(18000)) }
            runCatching { println("P4_CAMERA_SERVICE=" + shell("dumpsys media.camera").take(12000)) }
            runCatching { println("P4_CAMERA_LOG=" + shell("logcat -d -t 250 -s CameraX Camera2CameraImpl CameraStateRegistry AndroidRuntime").takeLast(12000)) }
            runCatching { TestEvidence.screenshot("p4-failure.png") }
            throw AssertionError("P4_STATE=" + activeModel?.state?.value?.let { "page=${it.page},busy=${it.busy},error=${it.error},lookup=${it.lookupState}" } +
                "\nP4_NATIVE_PACKAGE=" + device.currentPackageName + "\nP4_CAMERA_PERMISSION=" + instrumentation.targetContext.checkSelfPermission(android.Manifest.permission.CAMERA) + "\n" + error, error)
        }
    }
    private fun catalogFlow() {
        check(BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val fixture = JSONObject(shell("cat /data/local/tmp/p4-android.json"))
        val store = AccountStore(instrumentation.targetContext); store.clear()
        val api = AccountApi(store)
        runBlocking { api.accept(api.json("v1/auth/login","POST",JSONObject().put("email",fixture.getString("email")).put("password",fixture.getString("password")),false)) }
        val model = CatalogViewModel(CatalogRepository(api), { error("Not used") }, { error("Session expired") }).also { activeModel = it }
        compose.setContent {
            // The same production UI, repository, state and scanner implementations.
            KraPivaTheme { CatalogApp(api,store.session()!!.getString("userId"), { error("Not used") }, {}, {},model) }
        }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.items.any { it.optString("name") == "Synthetic Android Тёмный Ёж" } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Synthetic Android Тёмный Ёж"))
        compose.runOnIdle {
            model.lookup("WIFI:T:WPA;S:Synthetic;P:Synthetic;;","qr")
            assertEquals("unsupported_qr",model.state.value.lookupState)
            assertEquals("",model.state.value.code); assertFalse(model.state.value.busy)
        }
        screenshot("p4-catalog.png")
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("Название, пивоварня или стиль").performTextInput("темный еж stout")
        compose.onNodeWithText("Найти").performClick()
        compose.waitUntil(15_000) { !model.state.value.busy }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Synthetic Android Тёмный Ёж").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Synthetic Android Тёмный Ёж"))
        compose.onNodeWithText("Synthetic Android Тёмный Ёж").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Проверено по источнику").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p4-position.png")
        compose.onNodeWithText("Synthetic Android Brewery").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Позиции пивоварни").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p4-brewery.png")
        compose.onNodeWithText("Позиции пивоварни").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Сканировать штрихкод").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) {
            !model.state.value.busy && compose.onAllNodes(hasText("Сканировать штрихкод") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        // Wait for the actual enabled UI, then confirm navigation before looking for native UI.
        compose.onNodeWithText("Сканировать штрихкод").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.state.value.page == CatalogPage.SCANNER }
        // Compose's test clock only advances through Compose test APIs. Flush the screen's
        // LaunchedEffect before a real-time UiAutomator wait, or the dialog is never launched.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // Deny and then grant through the actual Android permission UI.
        val device = UiDevice.getInstance(instrumentation)
        val allow = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),10_000)
        assertNotNull("The native camera permission dialog must be visible",allow)
        if (allow != null) {
            val deny = device.findObject(androidx.test.uiautomator.By.res(java.util.regex.Pattern.compile(".*:id/permission_deny_button")))
            assertNotNull(deny); clickPermissionButton(deny.resourceName)
            assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res(deny.resourceName)),15_000))
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,instrumentation.targetContext.checkSelfPermission(android.Manifest.permission.CAMERA))
            waitForText("Разрешить камеру")
            compose.onNodeWithText("Введите EAN / UPC / GTIN").assertExists()
            screenshot("p4-scanner-denied.png")
            compose.onNodeWithText("Разрешить камеру").performScrollTo().performClick()
            val grant = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),10_000)
            assertNotNull(grant); clickPermissionButton(grant.resourceName)
            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.pkg(instrumentation.targetContext.packageName)),15_000))
        }
        assertNull("Permission actions must not submit the manual barcode form",model.state.value.error)
        waitForText("Камера готова · наведите на штрихкод",20_000)
        val cameraManager = instrumentation.targetContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val cameraId = cameraManager.cameraIdList.first { cameraManager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
        val cameraAvailable = java.util.concurrent.atomic.AtomicBoolean(true)
        val availability = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(id: String) { if (id == cameraId) cameraAvailable.set(true) }
            override fun onCameraUnavailable(id: String) { if (id == cameraId) cameraAvailable.set(false) }
        }
        cameraManager.registerAvailabilityCallback(availability,android.os.Handler(android.os.Looper.getMainLooper()))
        compose.waitUntil(10_000) { !cameraAvailable.get() }
        screenshot("p4-scanner-camera.png")
        compose.onNodeWithText("Введите EAN / UPC / GTIN").performScrollTo().performTextInput(fixture.getString("gtin"))
        compose.onNodeWithText("Проверить код").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Synthetic Android Тёмный Ёж").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { cameraAvailable.get() }
        cameraManager.unregisterAvailabilityCallback(availability)
        compose.onNodeWithText("Назад").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Проверить код").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Введите EAN / UPC / GTIN").performScrollTo().performTextReplacement("96385074")
        compose.onNodeWithText("Проверить код").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Позиция не найдена").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p4-scanner-unknown.png")
        compose.onNodeWithText("Предложить позицию").performScrollTo().performClick()
        compose.onNodeWithText("Название *").performScrollTo().performTextInput("Synthetic Android proposal")
        compose.onNodeWithText("Пивоварня *").performScrollTo().performTextInput("Synthetic new brewery")
        compose.onNodeWithText("Источник сведений *").performScrollTo().performTextInput("Synthetic own-label test")
        compose.onNodeWithText("Крепость, %").performScrollTo().performTextInput("NaN")
        compose.onNodeWithText("Отправить на проверку").performScrollTo().performClick()
        compose.onNodeWithText("Проверьте числовые поля").assertExists()
        compose.onNodeWithText("Крепость, %").performScrollTo().performTextReplacement("6,5")
        compose.onNodeWithText("Отправить на проверку").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Ожидает проверки").fetchSemanticsNodes().isNotEmpty() }
        screenshot("p4-proposals.png")
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
            compose.waitUntil(20_000) { !networkReady() }
            runBlocking {
                val reopened = AccountApi(store); assertTrue(reopened.restore())
                val cached = reopened.catalogGet("v1/positions/${fixture.getString("positionId")}")
                assertTrue(cached.second); assertEquals(fixture.getString("positionId"),cached.first.getString("id"))
            }
            compose.runOnIdle { model.detail(fixture.getString("positionId")) }
            compose.waitUntil(30_000) { model.state.value.page == CatalogPage.POSITION && model.state.value.offline && !model.state.value.busy }
            compose.onNodeWithText("Нет связи с сервером · сохранённая копия, до 24 часов. Статус мог измениться.").assertExists()
            screenshot("p4-offline-position.png")
        } finally { shell("svc wifi enable"); shell("svc data enable") }
        compose.waitUntil(30_000) { networkReady() }
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(20_000) { !model.state.value.busy && !model.state.value.offline }
        val owner = store.session()!!.getString("userId")
        store.clear(); assertNull(store.cachedCatalog(owner,"v1/positions/${fixture.getString("positionId")}")); store.close()
    }
}
