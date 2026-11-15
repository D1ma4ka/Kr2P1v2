package ru.krapiva.app.offline

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.krapiva.app.account.*
import ru.krapiva.app.diary.objects

class LocalStorageTest {
    @Test fun upgradePreservesEncryptedDraftAndCollectionsRemainOwnerScoped() = runBlocking {
        val context=IsolatedStoreContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            AccountStore(context).use { store ->
                store.saveSession(JSONObject().put("userId","owner-a"))
                store.saveDraft("owner-a","draft",JSONObject().put("draftId","draft").put("body","private-draft"))
                // Reproduce a v3 database using only this test's isolated database.
                store.writableDatabase.execSQL("DROP TABLE local_documents")
                store.writableDatabase.version=3
            }
            AccountStore(context).use { store ->
                assertEquals(4,store.readableDatabase.version)
                assertEquals("private-draft",store.drafts("owner-a").getJSONObject(0).getString("body"))
                val api=AccountApi(store);api.restore();val repo=CollectionsRepository(api)
                repo.create("PRIVATE-COLLECTION");repo.create("Second")
                val first=repo.all()[0].getString("id");val second=repo.all()[1].getString("id")
                repo.add(first,"position-can");repo.add(first,"position-can");repo.add(first,"position-bottle")
                assertEquals(2,repo.all()[0].getJSONArray("members").length())
                repo.move(first,second,"position-can",true)
                repo.move(first,second,"position-bottle",false)
                assertEquals(1,repo.all()[0].getJSONArray("members").objects().count{!it.optBoolean("deleted")})
                val bytes=store.readableDatabase.rawQuery("SELECT payload FROM local_documents",null).use{it.moveToFirst();it.getBlob(0)}
                assertFalse(String(bytes).contains("PRIVATE-COLLECTION"))
                api.clear(true);api.accept(JSONObject().put("userId","owner-b"))
                assertTrue(repo.all().isEmpty())
                api.accept(JSONObject().put("userId","owner-a"))
                assertEquals(2,repo.all().size)
                repo.delete(first)
                assertEquals(1,repo.all().size)
                assertTrue(repo.pendingCollections().any{it.optBoolean("deleted")})
            }
        } finally { context.dispose() }
    }
}
