package ru.krapiva.app.pets

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountViewModel
import java.time.Instant
import java.util.UUID

class PetAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tag(value:String) = compose.onNodeWithTag(value)
    private fun text(value:String) = compose.onNodeWithText(value)
    private fun awaitTag(tag:String) = compose.waitUntil(75_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitText(text:String) = compose.waitUntil(75_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun clickText(value:String) { awaitText(value);text(value).performScrollTo().performClick() }

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
            val candidates=listOf("Крапивка" to "starter_nettle","Краник" to "starter_tap","Крышечка" to "starter_cap",
                "Бутылочка" to "starter_bottle","Хмель" to "starter_hop")
            for((name,id) in candidates) {
                tag("pet-candidates")
                    .performScrollToNode(hasText(name))
                text(name).performClick()
                awaitTag("pet-preview-$id")
                TestEvidence.screenshot("p10-$id.png")
            }
            tag("pet-name").performScrollTo().performTextInput("Финик")
            tag("pet-adopt").performScrollTo().performClick()
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
        clickText("Магазин")
        awaitTag("pet-item-cookie")
        TestEvidence.screenshot("p10-kra-shop.png")
        tag("pet-item-cookie").performScrollTo().performClick()
        awaitTag("pet-buy")
        tag("pet-buy").performScrollTo()
        TestEvidence.screenshot("p10-purchase.png")
        tag("pet-buy").performClick()
        awaitTag("kra-room")
        assertEquals(1,state().getInt("balance"))
        compose.onNodeWithContentDescription("Подойти к столу").performScrollTo().performClick()
        awaitTag("pet-item-cookie")
        tag("pet-item-cookie").performScrollTo().performClick()
        awaitTag("kra-room")
        compose.waitUntil(75_000) { state().getJSONArray("inventory").length()==0 }

        compose.onNodeWithContentDescription("Открыть шкаф").performScrollTo().performClick()
        awaitText("Гардероб")
        TestEvidence.screenshot("p10-wardrobe.png")
        clickText("В комнату")
        val event=compose.onNodeWithTag("pet-page-EVENTS")
        tag("pet-room-actions")
            .performScrollToNode(hasTestTag("pet-page-EVENTS"))
        event.performScrollTo().performClick()
        awaitText("Код события")
        text("Код события").performScrollTo().performTextInput(data.getString("qr"))
        clickText("Получить награду")
        awaitTag("kra-room")
        val reward=state().getJSONArray("inventory").getJSONObject(0)
        assertEquals("partner_badge",reward.getString("definitionId"))
        val replay=runBlocking { api.json("v1/pets/redeem","POST",JSONObject().put("code",data.getString("qr"))) }
        assertEquals(reward.getString("id"),replay.getString("itemId"))
        assertEquals(1,state().getJSONArray("inventory").length())
        compose.activityRule.scenario.recreate()
        awaitTag("nav-PETS")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        awaitText("Финик")
        TestEvidence.screenshot("p10-room-light.png")
        compose.runOnIdle { model.navigate(ru.krapiva.app.account.AccountScreen.SETTINGS) }
        clickText("Тёмная")
        tag("nav-PETS").performClick()
        awaitTag("kra-room")
        TestEvidence.screenshot("p10-room-dark.png")
    }
}
