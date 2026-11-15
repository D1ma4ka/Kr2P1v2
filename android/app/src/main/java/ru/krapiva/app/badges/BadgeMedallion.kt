package ru.krapiva.app.badges

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ru.krapiva.app.designsystem.*

/** A finite decorative reveal. Compose also observes the platform animator duration scale. */
@Composable internal fun BadgeMedallion(earned:Boolean,pinned:Boolean=false) {
    var visible by remember(earned){mutableStateOf(false)}
    LaunchedEffect(earned){visible=true}
    val reduced=!ValueAnimator.areAnimatorsEnabled()
    val scale by animateFloatAsState(if(visible||!earned||reduced)1f else .86f,tween(if(reduced)0 else 260),label="badge-reveal")
    Box(Modifier.size(if(pinned)40.dp else 60.dp).scale(scale).background(
        if(earned)KraPivaColors.Amber.copy(alpha=.18f) else MaterialTheme.colorScheme.surfaceVariant,CircleShape),contentAlignment=Alignment.Center) {
        ClubGlyph(if(earned)ClubIcon.CHECK else ClubIcon.BOOKMARK,Modifier.size(if(pinned)22.dp else 32.dp))
    }
}
