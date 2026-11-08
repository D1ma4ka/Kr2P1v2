package ru.krapiva.app

import android.graphics.Color
import android.os.Bundle
import androidx.compose.runtime.SideEffect
import ru.krapiva.app.designsystem.ThemePreference
import ru.krapiva.app.designsystem.ThemeMode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.krapiva.app.designsystem.KraPivaTheme
import ru.krapiva.app.account.AccountApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val appearance = ThemePreference(this)
        setContent {
            val mode = appearance.mode
            SideEffect {
                val style = if (mode == ThemeMode.DARK) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            KraPivaTheme(mode, appearance::select) { AccountApp() }
        }
    }
}
