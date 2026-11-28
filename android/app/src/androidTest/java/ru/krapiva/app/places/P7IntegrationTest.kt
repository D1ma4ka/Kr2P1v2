package ru.krapiva.app.places

import android.Manifest
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
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
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.geometry.Point
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
        val native = compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)) }
        val model = compose.runOnIdle {
            ViewModelProvider(root.catalogOwner).get("places-${fixture.getString("userId")}", PlacesViewModel::class.java)
        }
        // The 12 fixture venues occupy three adjacent rows around this centroid.
        val centroid = Point(55.751, 37.6115)
        compose.runOnIdle { model.center(centroid.latitude, centroid.longitude, 12.0) }
        compose.waitUntil(30_000) { model.state.value.points.size >= 12 && !model.state.value.busy }
        fun screenPoint(point: Point): Offset = compose.runOnIdle {
            val pixel = requireNotNull(native.mapWindow.worldToScreen(point))
            Offset(pixel.x, pixel.y)
        }
        TestEvidence.screenshot("p7-native-cluster.png")
        var click = screenPoint(centroid)
        compose.onNodeWithTag("venue-map").performTouchInput { click(click) }
        compose.waitUntil(30_000) { compose.runOnIdle { native.mapWindow.map.cameraPosition.zoom > 12f } }
        // Real pinch and pan events, not a screenshot stand-in or a camera-only unit test.
        compose.onNodeWithTag("venue-map").performTouchInput {pinch(Offset(width*.35f,height*.4f),Offset(width*.65f,height*.6f),Offset(width*.15f,height*.3f),Offset(width*.85f,height*.7f),800)}
        var before=0.0
        compose.runOnIdle {before=native.mapWindow.map.cameraPosition.target.longitude}
        compose.onNodeWithTag("venue-map").performTouchInput {swipe(Offset(width*.5f,height*.6f),Offset(width*.6f,height*.6f),600)}
        compose.waitUntil(15_000){var moved=false;compose.runOnIdle {moved=native.mapWindow.map.cameraPosition.target.longitude!=before};moved}
        // Center a known real API fixture, select an actual rendered point and follow its profile.
        val id = fixture.getJSONArray("venueIds").getString(0)
        val row = runBlocking { root.api.json("v1/venues/$id") }
        val point = Point(row.getDouble("latitude"), row.getDouble("longitude"))
        compose.runOnIdle { model.center(point.latitude, point.longitude, 20.0) }
        compose.waitUntil(30_000) { model.state.value.points.any { it.getString("id") == id } && !model.state.value.busy }
        click = screenPoint(point)
        compose.onNodeWithTag("venue-map").performTouchInput {click(click)}
        compose.waitUntil(30_000){compose.onAllNodesWithText("Подробнее").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Подробнее").performClick()
        compose.onNodeWithText("Чекин в этом месте").performScrollTo().assertExists().performClick()
        compose.waitUntil(30_000){root.state.value.screen==AccountScreen.DIARY}
        assertNotNull(root.diaryVenue)
        assertEquals(id,root.diaryVenue!!.getString("id"))
        TestEvidence.screenshot("p7-checkin-place-picker.png")
    }
    /** Run on a fresh, isolated API 30+ emulator, with the P7 HTTP fixture and no MapKit key. */
    @Test fun nearbyPermissionCityDisabledProviderAndNetworkRecovery() {
        val fixture = login()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = compose.activity
        val location = context.getSystemService(android.location.LocationManager::class.java)!!
        val network = context.getSystemService(ConnectivityManager::class.java)!!
        assertFalse(mapConfigured)
        assertFalse(hasLocationPermission(context))
        compose.runOnIdle { root.openVenues() }
        val locateLabel = if (emulatedDevice) "Позиция эмулятора" else "Рядом со мной"
        compose.onNodeWithText(locateLabel).performClick()
        compose.onNodeWithText("Продолжить").performClick()
        val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 30_000)
        requireNotNull(deny).click()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Введите город вручную").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("P7 Synthetic city · 12").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("P7 Synthetic city · 12").performClick()
        compose.onNodeWithText(missingMapKey).assertExists()
        val model = compose.runOnIdle {
            ViewModelProvider(root.catalogOwner).get("places-${fixture.getString("userId")}", PlacesViewModel::class.java)
        }
        compose.waitUntil(30_000) { model.state.value.items.size == 12 }
        assertNull(model.state.value.deviceLocation)
        assertTrue(model.state.value.items.all { it.has("distanceKm") && it.getBoolean("testOnly") })
        try {
            device.executeShellCommand("cmd location set-location-enabled false")
            compose.waitUntil(15_000) { !location.isLocationEnabled }
            compose.onNodeWithText(locateLabel).performClick()
            compose.onNodeWithText("Продолжить").performClick()
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val approximate = device.wait(Until.findObject(By.text("Approximate")), 30_000)
                requireNotNull(approximate) { "Android approximate location choice" }.click()
            }
            val foreground = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_allow_foreground_only_button"))), 30_000)
            requireNotNull(foreground).click()
            compose.waitUntil(15_000) { hasLocationPermission(context) }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Геопозиция недоступна.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertNull(model.state.value.deviceLocation)
            device.executeShellCommand("cmd connectivity airplane-mode enable")
            compose.waitUntil(30_000) { network.activeNetwork == null }
            compose.onNodeWithText("Радиус · 5 км").performClick()
            compose.onNodeWithText("3 км").performClick()
            compose.waitUntil(45_000) { model.state.value.error != null }
            compose.onNodeWithText("Места не найдены.", substring = true).assertDoesNotExist()
        } finally {
            device.executeShellCommand("cmd connectivity airplane-mode disable")
            device.executeShellCommand("cmd location set-location-enabled true")
        }
        compose.waitUntil(30_000) { network.activeNetwork != null }
        compose.onNodeWithText("Повторить").performClick()
        compose.waitUntil(30_000) { model.state.value.items.size == 12 && model.state.value.error == null }
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
