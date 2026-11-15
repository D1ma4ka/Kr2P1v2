package ru.krapiva.app.offline

import ru.krapiva.app.account.AccountApi
import ru.krapiva.app.catalog.text
import ru.krapiva.app.diary.objects

/** Labels are read only from still-valid account cache, never from search history snapshots. */
suspend fun availablePositionLabels(api: AccountApi): Map<String,String> = buildMap {
    api.availableCache().forEach { (path,data) ->
        if(path.startsWith("v1/positions") && !path.endsWith("/statistics")) {
            val rows=data.optJSONArray("items")?.objects() ?: listOf(data)
            rows.forEach { row ->
                val id=row.text("id");val name=row.text("name")
                if(id!=null&&name!=null) {put(id,name);row.text("requestedId")?.let{put(it,name)}}
            }
        }
    }
}
