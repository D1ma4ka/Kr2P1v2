package ru.krapiva.app.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import ru.krapiva.app.welcome.ClubSplash

/** Host-driven sensor acceptance harness; never holds the production startup screen. */
class LoadingMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostDrivenTiltOnLoadingScreen() {
        compose.setContent { KraPivaTheme { ClubSplash() } }
        compose.onNodeWithTag("loading-beer").assertIsDisplayed()
        // Gives check_android_motion.py time to inject actual emulator sensor events
        // and inspect rendered foam. This test is invoked only by that host script.
        Thread.sleep(40_000)
    }
}
