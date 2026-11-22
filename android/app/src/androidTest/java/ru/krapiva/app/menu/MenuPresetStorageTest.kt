package ru.krapiva.app.menu

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.account.AccountStore
import ru.krapiva.app.offline.IsolatedStoreContext
import java.time.Instant

class MenuPresetStorageTest {
    @Test fun cachedMenuExpiresAndCheckinInvalidationPreservesSavedRules() = runBlocking {
        val context = IsolatedStoreContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val path = "v1/venues/test/menu"
        try {
            AccountStore(context).use { store ->
                val api = AccountApi(store)
                api.accept(JSONObject().put("userId", "a"))
                val presets = MenuPresetRepository(api, "a")
                presets.edit { it.save("mine", "Мои IPA", MenuFilter(styles = setOf("ipa")), Instant.EPOCH) }
                val payload = JSONObject().put("menu", JSONObject.NULL)
                store.cacheCatalog("a", path, payload)
                assertNull(store.cachedCatalog("b", path))
                assertNotNull(store.cachedCatalog("a", path))
                store.writableDatabase.execSQL("UPDATE catalog SET saved_at=0 WHERE user_id=? AND path=?", arrayOf("a", path))
                assertNull(store.cachedCatalog("a", path))
                store.cacheCatalog("a", path, payload)
                api.invalidateDiary()
                assertNull(store.cachedCatalog("a", path))
                assertEquals("Мои IPA", presets.read().title("mine"))
                store.cacheCatalog("a", path, payload)
                store.evictCatalog("a", path)
                assertNull(store.cachedCatalog("a", path))
            }
        } finally {
            context.dispose()
        }
    }

    @Test fun persistsAcrossReopenAndLogoutAndIsolatesAccounts() = runBlocking {
        val context = IsolatedStoreContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val rule = MenuFilter(styles = setOf("ipa", "apa"), abv = MenuRange("4", "8"),
            sort = MenuSort.RATING, tried = Tried.NO)
        try {
            val initial = AccountStore(context)
            try {
                val api = AccountApi(initial)
                api.accept(JSONObject().put("userId", "a"))
                MenuPresetRepository(api, "a").edit { it.save("test", "My IPA", rule, Instant.now()).selectDefault("test") }
                api.clear(preserveDrafts = true)
            } finally {
                initial.close()
            }
            val reopened = AccountStore(context)
            try {
                val api = AccountApi(reopened)
                api.accept(JSONObject().put("userId", "b"))
                assertTrue(MenuPresetRepository(api, "b").read().active.isEmpty())
                assertTrue(runCatching { MenuPresetRepository(api, "a").read() }.isFailure)
                api.accept(JSONObject().put("userId", "a"))
                val saved = MenuPresetRepository(api, "a").read()
                assertEquals(rule, saved.filter("test"))
                assertEquals("test", saved.defaultId)
            } finally {
                reopened.close()
            }
        } finally {
            context.dispose()
        }
    }
}
