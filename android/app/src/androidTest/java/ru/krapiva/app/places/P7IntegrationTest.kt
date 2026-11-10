package ru.krapiva.app.places

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountScreen
import ru.krapiva.app.account.AccountViewModel
import ru.krapiva.app.diary.objects

/** Explicit local synthetic fixture, real HTTP and native GL renderer; never part of P3AcceptanceSuite. */
class P7IntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var root: AccountViewModel
    private fun login(): JSONObject {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val fixture=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat /data/local/tmp/p7-android-fixture.json")).use {JSONObject(String(it.readBytes()))}
        compose.runOnIdle {root=ViewModelProvider(compose.activity)[AccountViewModel::class.java]}
        compose.waitUntil(60_000){!root.state.value.busy}
        compose.runOnIdle {root.login(fixture.getString("email"),fixture.getString("password"))}
        compose.waitUntil(60_000){!root.state.value.busy&&root.state.value.profile!=null}
        assertEquals(fixture.getString("userId"),root.state.value.profile!!.getString("userId"))
        return fixture
    }
    private fun findMap(view: View): MapView? {
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let {return it}
        return null
    }
    @Test fun nativeClustersGesturesMarkersDetailAndCheckinPicker() {
        val fixture=login()
        compose.runOnIdle {root.openVenues(fixture.getJSONArray("venueIds").getString(0))}
        compose.waitUntil(60_000){compose.onAllNodesWithText("На карте").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("На карте").performScrollTo().performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithTag("venue-map").fetchSemanticsNodes().isNotEmpty()}
        var native: MapLibreMap?=null
        compose.runOnIdle {findMap(compose.activity.window.decorView)!!.getMapAsync {native=it}}
        compose.waitUntil(30_000){native?.style?.isFullyLoaded==true}
        compose.onNodeWithText("Отдалить").performClick();compose.onNodeWithText("Отдалить").performClick()
        var cluster: Feature?=null
        compose.waitUntil(60_000) {compose.runOnIdle {cluster=native!!.queryRenderedFeatures(android.graphics.RectF(0f,0f,10000f,10000f),"clusters").firstOrNull()};cluster!=null}
        var zoom=0.0;var click=Offset.Zero
        compose.runOnIdle {
            zoom=native!!.cameraPosition.zoom
            val p=cluster!!.geometry() as Point
            val screen=native!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(p.latitude(),p.longitude()))
            click=Offset(screen.x,screen.y)
            assertTrue(cluster!!.getNumberProperty("point_count").toInt()>=2)
        }
        TestEvidence.screenshot("p7-native-cluster.png")
        compose.onNodeWithTag("venue-map").performTouchInput {click(click)}
        compose.waitUntil(30_000){var expanded=false;compose.runOnIdle {expanded=native!!.cameraPosition.zoom>zoom};expanded}
        // Real pinch and pan events, not a screenshot stand-in or a camera-only unit test.
        compose.onNodeWithTag("venue-map").performTouchInput {pinch(Offset(width*.35f,height*.4f),Offset(width*.65f,height*.6f),Offset(width*.15f,height*.3f),Offset(width*.85f,height*.7f),800)}
        var before=0.0
        compose.runOnIdle {before=native!!.cameraPosition.target!!.longitude}
        compose.onNodeWithTag("venue-map").performTouchInput {swipe(Offset(width*.5f,height*.6f),Offset(width*.6f,height*.6f),600)}
        compose.waitUntil(15_000){var moved=false;compose.runOnIdle {moved=native!!.cameraPosition.target!!.longitude!=before};moved}
        // Center a known real API fixture, select an actual rendered point and follow its profile.
        compose.runOnIdle {val m=ViewModelProvider(root.catalogOwner).get("places-${fixture.getString("userId")}",PlacesViewModel::class.java);m.center(55.75,37.61,19.0)}
        var point: Feature?=null
        compose.waitUntil(60_000){compose.runOnIdle {point=native!!.queryRenderedFeatures(android.graphics.RectF(0f,0f,10000f,10000f),"points").firstOrNull()};point!=null}
        compose.runOnIdle {val p=point!!.geometry() as Point;val xy=native!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(p.latitude(),p.longitude()));click=Offset(xy.x,xy.y)}
        compose.onNodeWithTag("venue-map").performTouchInput {click(click)}
        compose.waitUntil(30_000){compose.onAllNodesWithText("Подробнее").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Подробнее").performClick()
        compose.onNodeWithText("Чекин в этом месте").performScrollTo().assertExists().performClick()
        compose.waitUntil(30_000){root.state.value.screen==AccountScreen.DIARY}
        assertNotNull(root.diaryVenue)
        assertEquals(point!!.getStringProperty("id"),root.diaryVenue!!.getString("id"))
        TestEvidence.screenshot("p7-checkin-place-picker.png")
    }
    @Test fun serverBadgesPersistThreePinsAndRejectFourthInUi() {
        val fixture=login()
        runBlocking {
            val positions=fixture.getJSONArray("positionIds")
            for(i in 0 until positions.length())root.api.json("v1/checkins","POST",JSONObject()
                .put("positionId",positions.getString(i)).put("body","Synthetic P7 note").put("ratingQuarters",17)
                .put("occurredAt",fixture.getString("occurredAt")).put("visibility","private")
                .put("venueId",fixture.getJSONArray("venueIds").getString(i%2)),requestId=fixture.getJSONArray("requestIds").getString(i))
            root.api.json("v1/me/badge-pins","PUT",JSONObject().put("ids",JSONArray()))
        }
        val earned=runBlocking {root.api.json("v1/me/badges").getJSONArray("items").objects().filter {!it.isNull("earnedAt")}}
        assertTrue(earned.size>=4)
        compose.runOnIdle {root.navigate(AccountScreen.BADGES)}
        compose.waitUntil(30_000){compose.onAllNodesWithTag("badges-list").fetchSemanticsNodes().isNotEmpty()}
        for(badge in earned.take(3)) {
            val tag="badge-pin-${badge.getString("id")}"
            compose.onNodeWithTag("badges-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.waitUntil(30_000){compose.onAllNodesWithText("Открепить · место ${earned.indexOf(badge)+1}").fetchSemanticsNodes().isNotEmpty()}
        }
        val fourth="badge-pin-${earned[3].getString("id")}"
        compose.onNodeWithTag("badges-list").performScrollToNode(hasTestTag(fourth));compose.onNodeWithTag(fourth).assertIsNotEnabled()
        assertEquals(3,runBlocking {root.api.json("v1/me/badges").getJSONArray("items").objects().count {!it.isNull("pinSlot")}})
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000){root.state.value.profile!=null}
        TestEvidence.screenshot("p7-badges-three-pins.png")
    }
}
