package ru.krapiva.app.pets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountViewModel
import java.time.Instant
import java.util.UUID

class PetAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val starters = listOf("Крапивка" to "starter_nettle", "Краник" to "starter_tap",
        "Крышечка" to "starter_cap", "Бутылочка" to "starter_bottle", "Хмель" to "starter_hop")

    private fun tag(value:String) = compose.onNodeWithTag(value)
    private fun text(value:String) = compose.onNodeWithText(value)
    private fun awaitTag(tag:String) = compose.waitUntil(75_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitText(text:String) = compose.waitUntil(75_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitPet(equipped:Boolean=false) = compose.waitUntil(75_000) {
        val marker=hasContentDescription("Питомец")
        val match=if(equipped) marker and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Предметов надето: 5"
        ) else marker
        compose.onAllNodes(match).fetchSemanticsNodes().isNotEmpty()
    }
    private fun clickText(value:String) { awaitText(value);reveal(text(value)).performClick() }

    private fun reveal(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
        // Compose 1.8.0 performScrollTo reads live layout coordinates on the instrumentation thread.
        repeat(4) {
            val node = target.fetchSemanticsNode()
            val scrolled = compose.runOnUiThread {
                val bounds = Rect(node.positionInRoot, node.size.toSize())
                for (parent in generateSequence(node.parent) { it.parent }) {
                    val action = parent.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: continue
                    val coordinates = parent.layoutInfo.coordinates
                    val viewport = coordinates.boundsInParent().translate(
                        coordinates.parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero
                    )
                    val x = parent.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                    val y = parent.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    val dx = if (x == null) 0f else scrollDelta(bounds.left, bounds.right, viewport.left, viewport.right)
                    val dy = if (y == null) 0f else scrollDelta(bounds.top, bounds.bottom, viewport.top, viewport.bottom)
                    if ((dx != 0f || dy != 0f) && action(dx, dy)) return@runOnUiThread true
                }
                false
            }
            if (!scrolled) return target
        }
        return target
    }

    private fun scrollDelta(start: Float, end: Float, visibleStart: Float, visibleEnd: Float): Float = when {
        start < visibleStart && end <= visibleEnd -> start - visibleStart
        end > visibleEnd && start >= visibleStart -> end - visibleEnd
        else -> 0f
    }

    @Test fun realRoomCatalogWalletAndPartnerReward() {
        val device=InstrumentationRegistry.getInstrumentation()
        val data=ParcelFileDescriptor.AutoCloseInputStream(device.uiAutomation.executeShellCommand("cat /data/local/tmp/p10-fixture.json"))
            .use { JSONObject(String(it.readBytes())) }
        lateinit var model:AccountViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(75_000) { !model.state.value.busy }
        compose.runOnIdle { model.login(data.getString("email"),data.getString("password")) }
        compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile!=null }
        awaitTag("nav-HOME")
        val api=model.api
        fun state()=runBlocking { api.json("v1/pets/state") }
        if(InstrumentationRegistry.getArguments().getString("resume")=="true") {
            assertEquals(2,state().getInt("balance"))
            tag("nav-PETS").performClick()
        } else {
            assertFalse(state().has("pet") && !state().isNull("pet"))

            tag("nav-PETS").performClick()
            awaitTag("pet-name")
            for((name,id) in starters) {
                reveal(text(name)).performClick()
                awaitTag("pet-preview-$id")
                TestEvidence.screenshot("p10-$id.png")
            }
            reveal(tag("pet-name")).performTextInput("Финик")
            reveal(tag("pet-adopt")).performClick()
            awaitTag("kra-room")
            awaitText("Финик")
            assertEquals(0,state().getInt("balance"))
            TestEvidence.screenshot("p10-kra-room.png")

            val key=UUID.randomUUID().toString()
            val entry=JSONObject().put("positionId",data.getString("position")).put("ratingQuarters",JSONObject.NULL)
                .put("body","P10 local QA").put("occurredAt",Instant.now().toString()).put("visibility","private")
            runBlocking {
                val first=api.json("v1/checkins","POST",entry,requestId=key)
                assertEquals(first.getString("id"),api.json("v1/checkins","POST",entry,requestId=key).getString("id"))
            }
            tag("nav-HOME").performClick()
            tag("nav-PETS").performClick()
        }
        compose.waitUntil(75_000) { compose.onAllNodesWithText("2 KC",substring=true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2,state().getInt("balance"))
        clickText("Магазин")
        awaitTag("pet-item-cookie")
        TestEvidence.screenshot("p10-kra-shop.png")
        reveal(tag("pet-item-cookie")).performClick()
        val buy="Купить «Печенье» за 1 KC"
        awaitText(buy)
        reveal(text(buy))
        TestEvidence.screenshot("p10-purchase.png")
        text(buy).performClick()
        awaitText("KraRoom")
        assertEquals(1,state().getInt("balance"))
        reveal(compose.onNodeWithContentDescription("Подойти к столу")).performClick()
        awaitTag("pet-item-cookie")
        reveal(tag("pet-item-cookie")).performClick()
        awaitText("KraRoom")
        assertEquals(0,state().getJSONArray("inventory").length())

        reveal(compose.onNodeWithContentDescription("Открыть шкаф")).performClick()
        awaitText("Гардероб")
        TestEvidence.screenshot("p10-wardrobe.png")
        clickText("В комнату")
        val event=compose.onNodeWithTag("pet-page-EVENTS")
        reveal(event).performClick()
        awaitText("Код события")
        reveal(text("Код события")).performTextInput(data.getString("qr"))
        clickText("Получить награду")
        awaitText("KraRoom")
        awaitTag("pet-gesture-celebrate")
        val reward=state().getJSONArray("inventory").getJSONObject(0)
        assertEquals("partner_badge",reward.getString("definitionId"))
        val replay=runBlocking { api.json("v1/pets/redeem","POST",JSONObject().put("code",data.getString("qr"))) }
        assertEquals(reward.getString("id"),replay.getString("itemId"))
        assertEquals(1,state().getJSONArray("inventory").length())
        reveal(event).performClick()
        awaitText("Код события")
        reveal(text("Код события")).performTextInput(data.getString("qr"))
        clickText("Получить награду")
        awaitText("Эта награда уже в коллекции")
        assertEquals(reward.getString("id"),state().getJSONArray("inventory").getJSONObject(0).getString("id"))
        compose.activityRule.scenario.recreate()
        awaitTag("nav-PETS")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        awaitText("Финик")
        awaitPet()
        TestEvidence.screenshot("p10-room-light.png")
        compose.runOnIdle { model.navigate(ru.krapiva.app.account.AccountScreen.SETTINGS) }
        clickText("Тёмная")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        awaitPet()
        TestEvidence.screenshot("p10-room-dark.png")
        compose.runOnIdle { model.navigate(ru.krapiva.app.account.AccountScreen.SETTINGS) }
        clickText("Светлая")
    }

    @Test fun eachApprovedStarterCanBeAdopted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p10-fixture.json")
        ).use { JSONObject(String(it.readBytes())) }
        val accounts = fixture.getJSONArray("starterAccounts")
        assertEquals(starters.size, accounts.length())
        lateinit var model: AccountViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(75_000) { !model.state.value.busy }
        for ((index, starter) in starters.withIndex()) {
            val (label, id) = starter
            val account = accounts.getJSONObject(index)
            val name = "Питомец QA ${index + 1}"
            compose.runOnIdle { model.login(account.getString("email"), account.getString("password")) }
            compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile != null }
            awaitTag("nav-HOME")
            tag("kra-room").assertDoesNotExist()
            tag("nav-PETS").performClick()
            awaitTag("pet-name")
            clickText(label)
            awaitTag("pet-preview-$id")
            reveal(tag("pet-name")).performTextInput(name)
            reveal(tag("pet-adopt")).performClick()
            awaitTag("kra-room")
            awaitText(name)
            awaitPet()
            val pet = runBlocking { model.api.json("v1/pets/state").getJSONObject("pet") }
            assertEquals(id, pet.getString("definitionId"))
            assertEquals(name, pet.getString("name"))
            TestEvidence.screenshot("p10-adopted-$id.png")
            compose.runOnIdle { model.logout() }
            compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile == null }
        }
    }

    @Test fun renderKraPetFramesOnAndroidCanvas() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        for ((_, id) in starters) {
            val start = System.nanoTime()
            val rig = runBlocking { PetRig.load(assets, id, full = true) }
            val loadMillis = (System.nanoTime() - start) / 1_000_000
            val draw = rig.renderer(emptyList())
            val bitmap = Bitmap.createBitmap(184, 208, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                val scale = bitmap.width * .89f / rig.width
                canvas.translate((bitmap.width - rig.width * scale) / 2, (bitmap.height - rig.height * scale) / 2)
                canvas.scale(scale, scale)
                fun frame(elapsed: Float, clip: String = "eat") {
                    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                    draw(canvas, clip, elapsed)
                }
                fun pixels() = IntArray(bitmap.width * bitmap.height).also {
                    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                }
                frame(0f)
                val first = pixels()
                assertTrue(first.any { it ushr 24 != 0 })
                frame(rig.transitionDuration("sit"), "sit")
                assertTrue("Meal starts in the settled seat for $id", first.contentEquals(pixels()))
                frame(950f, "sit")
                assertFalse("Seated blink must change the rendered frame for $id", first.contentEquals(pixels()))
                frame(1600f)
                assertFalse(first.contentEquals(pixels()))
                frame(0f, "gift")
                val beforeGift = pixels()
                frame(590f, "gift")
                assertFalse("Gift animation must change the rendered frame for $id", beforeGift.contentEquals(pixels()))
                repeat(20) { frame(it * 93f) }
                val samples = LongArray(120) { index ->
                    val before = System.nanoTime()
                    frame(index * 43f % rig.duration("eat"))
                    (System.nanoTime() - before) / 1_000
                }.sortedArray()
                Log.i("KraPetPerf", "$id load_ms=$loadMillis canvas_median_us=${samples[59]} canvas_p95_us=${samples[113]}")
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun partnerCameraReadsQrAndRejectsReplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p10-fixture.json")
        ).use { JSONObject(String(it.readBytes())) }
        val account = fixture.getJSONArray("starterAccounts").getJSONObject(0)
        val rewardCode = fixture.getString("qr")
        lateinit var model: AccountViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(75_000) { !model.state.value.busy }
        compose.runOnIdle { model.login(account.getString("email"), account.getString("password")) }
        compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile != null }
        awaitTag("nav-PETS")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        fun scan() {
            reveal(tag("pet-page-EVENTS")).performClick()
            clickText("Сканировать QR")
            compose.waitUntil(75_000) {
                // Avoid logging the redemption code even if the frame cannot be decoded.
                compose.onAllNodesWithText(rewardCode).fetchSemanticsNodes().isNotEmpty()
            }
            clickText("Получить награду")
            awaitTag("kra-room")
        }
        fun inventory() = runBlocking { model.api.json("v1/pets/state").getJSONArray("inventory") }

        assertEquals(0, inventory().length())
        scan()
        val reward = inventory().getJSONObject(0)
        assertEquals("partner_badge", reward.getString("definitionId"))
        scan()
        awaitText("Эта награда уже в коллекции")
        assertEquals(1, inventory().length())
        assertEquals(reward.getString("id"), inventory().getJSONObject(0).getString("id"))
        TestEvidence.screenshot("p10-camera-replay.png")
    }

    @Test fun coldStartWithNoBackendRestoresPetAndBalance() {
        awaitTag("nav-PETS")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        awaitText("Сохранённые данные · офлайн")
        awaitText("Финик")
        awaitPet()
        tag("pet-balance").assertTextContains("1 KC", substring = true)
        TestEvidence.screenshot("p10-cold-offline.png")
    }

    @SdkSuppress(minSdkVersion = 35)
    @Test fun systemStatusBarMatchesRoomThemeAcrossRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p10-fixture.json")
        ).use { JSONObject(String(it.readBytes())) }
        lateinit var model: AccountViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(75_000) { !model.state.value.busy }
        compose.runOnIdle { model.login(fixture.getString("email"), fixture.getString("password")) }
        compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile != null }

        val mismatches = mutableListOf<String>()
        fun sample(stage: String, dark: Boolean) {
            compose.waitForIdle()
            val activity = compose.activity
            val (inset, lightAppearance) = compose.runOnUiThread {
                val window = activity.window
                val height = requireNotNull(window.decorView.rootWindowInsets)
                    .getInsets(WindowInsets.Type.statusBars()).top
                val appearance = requireNotNull(window.insetsController).systemBarsAppearance
                height to (appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0)
            }
            assertTrue("Status bar should be visible", inset > 0)
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val luminance = try {
                ColorUtils.calculateLuminance(screenshot.getPixel(screenshot.width / 2, inset / 2))
            } finally {
                screenshot.recycle()
            }
            if (lightAppearance == dark || (if (dark) luminance > 0.12 else luminance < 0.65)) {
                mismatches += "$stage, lightAppearance=$lightAppearance, luminance=$luminance"
            }
            TestEvidence.screenshot("p10-status-$stage.png")
        }

        fun openRoom() {
            awaitTag("nav-PETS")
            tag("nav-PETS").performClick()
            awaitTag("kra-room")
            awaitPet()
        }
        fun switchTheme(label: String) {
            compose.runOnIdle { model.navigate(ru.krapiva.app.account.AccountScreen.SETTINGS) }
            clickText(label)
            openRoom()
        }
        openRoom()
        sample("launch", dark = false)
        switchTheme("Тёмная")
        sample("dark-switch", dark = true)
        compose.activityRule.scenario.recreate()
        openRoom()
        sample("dark-recreated", dark = true)
        switchTheme("Светлая")
        sample("light-switch", dark = false)
        compose.activityRule.scenario.recreate()
        openRoom()
        sample("light-recreated", dark = false)
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test fun equippedRoomRendersAllFiveItems() {
        val device=InstrumentationRegistry.getInstrumentation()
        val data=ParcelFileDescriptor.AutoCloseInputStream(device.uiAutomation.executeShellCommand("cat /data/local/tmp/p10-equipment.json"))
            .use { JSONObject(String(it.readBytes())) }
        lateinit var model:AccountViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(75_000) { !model.state.value.busy }
        compose.runOnIdle { model.login(data.getString("email"),data.getString("password")) }
        compose.waitUntil(75_000) { !model.state.value.busy && model.state.value.profile!=null }
        val equipped=runBlocking { model.api.json("v1/pets/state").getJSONObject("equipped") }
        assertEquals(setOf("torso_base","torso_outer","face","head","wrist"),equipped.keys().asSequence().toSet())
        awaitTag("nav-PETS")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        awaitText("Солод")
        awaitPet(equipped=true)
        TestEvidence.screenshot("p10-dressed.png")
    }

}
