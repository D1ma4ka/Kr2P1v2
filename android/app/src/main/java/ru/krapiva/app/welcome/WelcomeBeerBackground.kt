package ru.krapiva.app.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import ru.krapiva.app.designsystem.*

/** Original KraPiva liquid, confined below the brand; never participates in hit testing. */
@Composable fun WelcomeBeerBackground(modifier: Modifier = Modifier) {
    val background = KraPivaColors.Ink
    Box(modifier) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.42f)) {
            ClubBeer(Modifier.matchParentSize(), backdrop = true, animateBackdrop = true)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(background, background.copy(alpha = .16f)))))
        }
    }
}
