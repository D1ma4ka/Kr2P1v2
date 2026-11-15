package ru.krapiva.app.offline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.account.*
import ru.krapiva.app.diary.*
import ru.krapiva.app.designsystem.*

class EditorOfflineTest {
    @get:Rule val compose = createComposeRule()
    @Test fun closeImmediatelyPersistsLatestFieldsWithoutBackend() {
        val context = IsolatedStoreContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val store = AccountStore(context)
        try {
            store.saveSession(JSONObject().put("userId","synthetic-owner"))
            val api = AccountApi(store)
            runBlocking { api.restore() }
            val model = DiaryViewModel(api,{ error("No uploads in offline test") },{})
            val draft = JSONObject().put("draftId","synthetic-draft").put("positionId","synthetic-position")
                .put("occurredAt","2026-01-01T10:00:00Z").put("ratingQuarters",JSONObject.NULL)
                .put("localPhotos",JSONArray()).put("mediaIds",JSONArray()).put("tagIds",JSONArray())
            compose.setContent { KraPivaTheme { DiaryEditor(DiaryState(editor=draft),model,{}, {}) } }
            compose.onNodeWithText("Заметка о пиве").performTextInput("Последние изменения")
            compose.onNodeWithText("Банка",useUnmergedTree=true).performScrollTo().performClick()
            var left = false
            compose.runOnIdle { model.leaveEditor { left = true } }
            compose.waitUntil(5000) { left }
            val saved = store.drafts("synthetic-owner").getJSONObject(0)
            assertEquals("Последние изменения",saved.getString("body"))
            assertEquals("Банка",saved.getString("servingFormat"))
            assertTrue(saved.isNull("ratingQuarters"))
            assertEquals(0,store.drafts("other-owner").length())
            store.close()
            AccountStore(context).use { assertEquals("Последние изменения",it.drafts("synthetic-owner").getJSONObject(0).getString("body")) }
        } finally { store.close();context.dispose() }
    }
}
