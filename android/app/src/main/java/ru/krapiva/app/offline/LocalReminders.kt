package ru.krapiva.app.offline

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import ru.krapiva.app.MainActivity
import ru.krapiva.app.R
import ru.krapiva.app.account.*
import ru.krapiva.app.diary.objects
import java.time.Instant
import java.util.UUID

interface FutureNotificationSource { suspend fun events(): List<JSONObject> }
class ReminderRepository(private val api:AccountApi) {
    suspend fun read()=api.localDocument("notifications-v1")
    suspend fun enabled(value:Boolean) {api.editLocalDocument("notifications-v1"){data->
        data.put("enabled",value)
        if(!value)data.optJSONArray("items")?.objects()?.filter{it.optLong("dueAt")>System.currentTimeMillis()}?.forEach{it.put("cancelled",true)}
    }}
    suspend fun add(id:String,kind:String,dueAt:Long) {
        require(kind in setOf("DRAFTS","COLLECTIONS"))
        api.editLocalDocument("notifications-v1"){data->
            check(data.optBoolean("enabled")){"Включите напоминания"}
            val rows=data.optJSONArray("items") ?: JSONArray()
            require(rows.objects().count{!it.optBoolean("deleted")}<100){"Удалите ненужные уведомления (лимит 100)"}
            val now=Instant.now().toString()
            rows.put(JSONObject().put("id",id).put("ownerUserId",api.ownerId()).put("kind",kind)
                .put("dueAt",dueAt).put("createdAt",now).put("updatedAt",now).put("localVersion",1)
                .put("read",false).put("deleted",false).put("cancelled",false))
            data.put("schemaVersion",1).put("items",rows)
        }
    }
    suspend fun change(id:String,delete:Boolean=false) {api.editLocalDocument("notifications-v1"){data->
        val row=data.getJSONArray("items").objects().first{it.getString("id")==id}
        row.put(if(delete)"deleted" else "read",true).put("updatedAt",Instant.now().toString()).put("localVersion",row.optLong("localVersion")+1)
    }}
}
object ReminderJobs {
    private const val CHANNEL="personal_reminders"
    fun allowed(context:Context)= (Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun schedule(context:Context,owner:String,id:String,kind:String,dueAt:Long) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        val occupied=scheduler.allPendingJobs.map{it.id}.toSet()
        val jobId=(5000..5999).firstOrNull{it !in occupied} ?: error("Слишком много напоминаний")
        val extra=PersistableBundle().apply{putString("owner",owner);putString("reminder",id);putString("kind",kind)}
        val job=JobInfo.Builder(jobId,ComponentName(context,ReminderJobService::class.java)).setMinimumLatency((dueAt-System.currentTimeMillis()).coerceAtLeast(1000))
            .setPersisted(true).setExtras(extra).build()
        check(scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS){"Android не смог запланировать напоминание"}
    }
    fun cancel(context:Context,owner:String,id:String?=null) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        scheduler.allPendingJobs.filter{it.extras.getString("owner")==owner&&(id==null||it.extras.getString("reminder")==id)}.forEach{scheduler.cancel(it.id)}
        if(id!=null)context.getSystemService(NotificationManager::class.java).cancel(id,1)
    }
    fun notify(context:Context,owner:String,id:String,kind:String) {
        if(!allowed(context))return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Личные напоминания",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data=android.net.Uri.parse("krapiva-local://reminder/$id")
            putExtra("reminderOwner",owner);putExtra("reminderPage",kind)
        }
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle("Личное напоминание KraPiva").setContentText("Откройте приложение, чтобы вернуться к сохранённым данным.")
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(id,1,notification)
    }
}
class ReminderJobService:JobService() {
    private val jobs=mutableMapOf<Int,Job>()
    override fun onStartJob(params:JobParameters):Boolean {
        jobs[params.jobId]=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                AccountStore(applicationContext).use { store ->
                    val owner=params.extras.getString("owner") ?: return@use
                    if(store.session()?.optString("userId")!=owner)return@use
                    val api=AccountApi(store);if(!api.restore()||api.ownerId()!=owner)return@use
                    val data=ReminderRepository(api).read()
                    val row=data.optJSONArray("items")?.objects()?.firstOrNull{it.optString("id")==params.extras.getString("reminder")} ?: return@use
                    if(data.optBoolean("enabled")&&!row.optBoolean("deleted")&&!row.optBoolean("cancelled")&&row.optLong("dueAt")<=System.currentTimeMillis()) {
                        if(store.session()?.optString("userId")==owner)ReminderJobs.notify(applicationContext,owner,row.getString("id"),row.getString("kind"))
                    }
                }
            } catch(e:Exception){if(e is CancellationException)throw e}
            finally {withContext(NonCancellable+Dispatchers.Main){if(jobs.remove(params.jobId)!=null)jobFinished(params,false)}}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {jobs.remove(params.jobId)?.cancel();return true}
    override fun onDestroy(){jobs.values.forEach{it.cancel()};jobs.clear();super.onDestroy()}
}
