package ru.krapiva.app.offline

import android.app.job.JobScheduler
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.krapiva.app.account.*
import ru.krapiva.app.diary.objects
import java.util.UUID

class RemindersTest {
    @Test fun optInInboxPersistsAndJobsCancelWithoutRealAccountData()=runBlocking {
        val realContext=InstrumentationRegistry.getInstrumentation().targetContext
        val context=IsolatedStoreContext(realContext)
        val owner="synthetic-${UUID.randomUUID()}";val id=UUID.randomUUID().toString()
        try {
            AccountStore(context).use { store ->
                store.saveSession(JSONObject().put("userId",owner));val api=AccountApi(store);api.restore();val repo=ReminderRepository(api)
                assertFalse(repo.read().optBoolean("enabled"))
                try{repo.add(id,"DRAFTS",1);fail("opt in required")}catch(_:IllegalStateException){}
                repo.enabled(true);repo.add(id,"DRAFTS",1)
                repo.change(id);assertTrue(repo.read().getJSONArray("items").getJSONObject(0).getBoolean("read"))
                repo.change(id,true);assertTrue(repo.read().getJSONArray("items").getJSONObject(0).getBoolean("deleted"))
                ReminderJobs.schedule(realContext,owner,id,"DRAFTS",System.currentTimeMillis()+3_600_000)
                val job=realContext.getSystemService(JobScheduler::class.java).allPendingJobs.first{it.extras.getString("reminder")==id}
                assertTrue(job.isPersisted);assertEquals(owner,job.extras.getString("owner"));assertEquals("DRAFTS",job.extras.getString("kind"))
                ReminderJobs.cancel(realContext,owner,id)
                assertFalse(realContext.getSystemService(JobScheduler::class.java).allPendingJobs.any{it.extras.getString("reminder")==id})
                api.accept(JSONObject().put("userId","other-synthetic"));assertFalse(repo.read().has("items"))
            }
        } finally {ReminderJobs.cancel(realContext,owner,id);context.dispose()}
    }
}
