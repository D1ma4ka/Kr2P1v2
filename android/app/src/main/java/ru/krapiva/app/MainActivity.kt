package ru.krapiva.app

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import ru.krapiva.app.designsystem.ThemePreference
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.krapiva.app.designsystem.KraPivaTheme
import ru.krapiva.app.account.AccountApp

class MainActivity : ComponentActivity() {
    private var reminderRequest by mutableStateOf<Pair<String,String>?>(null)
    private fun readReminder(value: Intent?) {
        val owner=value?.getStringExtra("reminderOwner");val page=value?.getStringExtra("reminderPage")
        if(owner!=null && page in setOf("DRAFTS","COLLECTIONS")) reminderRequest=owner to page!!
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);readReminder(intent) }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        readReminder(intent)
        val appearance = ThemePreference(this)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        updateSystemBars(appearance.mode.isDark(systemDark))
        setContent {
            val mode = appearance.mode
            val dark = mode.isDark(isSystemInDarkTheme())
            SideEffect { updateSystemBars(dark) }
            KraPivaTheme(mode, appearance::select) { AccountApp(reminderRequest=reminderRequest,consumeReminder={
                reminderRequest=null;intent.removeExtra("reminderOwner");intent.removeExtra("reminderPage")
            }) }
        }
    }

    private fun updateSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
