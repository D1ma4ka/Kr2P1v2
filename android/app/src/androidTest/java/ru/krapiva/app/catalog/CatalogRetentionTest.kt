package ru.krapiva.app.catalog

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.krapiva.app.BuildConfig
import ru.krapiva.app.MainActivity
import ru.krapiva.app.TestEvidence
import ru.krapiva.app.account.AccountScreen
import ru.krapiva.app.account.AccountStore
import ru.krapiva.app.account.AccountViewModel

/** Actual MainActivity recreation: retained account scope and real server logout. */
@RunWith(AndroidJUnit4::class)
class CatalogRetentionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun recreationRetainsCatalogAndLogoutClearsItsScope() {
        check(BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/p4-android.json")).use { JSONObject(String(it.readBytes())) }
        lateinit var root: AccountViewModel
        compose.runOnIdle { root = ViewModelProvider(compose.activity)[AccountViewModel::class.java] }
        compose.waitUntil(15_000) { !root.state.value.busy }
        compose.runOnIdle { root.login(fixture.getString("email"),fixture.getString("password")) }
        compose.waitUntil(20_000) { root.state.value.profile != null && !root.state.value.busy }
        val user = root.state.value.profile!!.getString("userId")
        compose.runOnIdle { root.navigate(AccountScreen.CATALOG) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Каталог пива").fetchSemanticsNodes().isNotEmpty() }
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle { catalog = ViewModelProvider(root.catalogOwner).get("catalog-$user",CatalogViewModel::class.java) }
        compose.waitUntil(20_000) { !catalog.state.value.busy && catalog.state.value.items.isNotEmpty() }
        compose.runOnIdle { catalog.filters(CatalogFilters(q="темный",sort="name")); catalog.refresh() }
        compose.waitUntil(15_000) { !catalog.state.value.busy }
        compose.runOnIdle { catalog.detail(fixture.getString("positionId")) }
        compose.waitUntil(15_000) { catalog.state.value.page == CatalogPage.POSITION && !catalog.state.value.busy }
        val scopeJob = catalog.viewModelScope.coroutineContext[Job]!!
        val oldActivity = compose.activity
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText("Проверено по источнику").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.runOnIdle {
            assertNotSame(oldActivity,compose.activity)
            val reopenedRoot = ViewModelProvider(compose.activity)[AccountViewModel::class.java]
            assertSame(root,reopenedRoot)
            val reopenedCatalog = ViewModelProvider(reopenedRoot.catalogOwner).get("catalog-$user",CatalogViewModel::class.java)
            assertSame(catalog,reopenedCatalog)
            assertEquals(CatalogPage.POSITION,reopenedCatalog.state.value.page)
            assertEquals("темный",reopenedCatalog.state.value.filters.q)
            assertEquals(fixture.getString("positionId"),reopenedCatalog.state.value.detail!!.getString("id"))
        }
        compose.waitForIdle()
        TestEvidence.screenshot("p4-recreated-position.png")
        val store = AccountStore(instrumentation.targetContext)
        try {
            assertNotNull(store.cachedCatalog(user,"v1/positions/${fixture.getString("positionId")}"))
            compose.runOnIdle { root.logout() }
            compose.waitUntil(15_000) { root.state.value.screen == AccountScreen.LOGIN && !root.state.value.busy }
            assertTrue(scopeJob.isCancelled)
            assertNull(store.session())
            assertNull(store.cachedCatalog(user,"v1/positions/${fixture.getString("positionId")}"))
        } finally { store.close() }
    }
}
