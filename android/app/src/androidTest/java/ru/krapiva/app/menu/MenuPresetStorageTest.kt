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
