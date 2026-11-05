package ru.krapiva.app.designsystem

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class BeerHeroTest {
    @get:Rule val compose = createComposeRule()
    @Test fun boundedLeftRightAndStaticAreVisible() {
        compose.setContent { KraPivaTheme { Column {
            Text("KraPiva — liquid states")
            BeerHero(previewAngle = -8f)
            BeerHero(previewAngle = 8f)
            BeerHero(forceReducedMotion = true)
        } } }
        compose.onAllNodesWithContentDescription("Фирменный бокал KraPiva с пеной и хмелем").assertCountEquals(3)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "p3-liquid-states.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
