package ru.krapiva.app.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.krapiva.app.designsystem.*

/** Only shown during real session restoration. Skipping affects animation, never authentication. */
@Composable fun ClubSplash() {
    var still by rememberSaveable {mutableStateOf(false)}
    Surface(Modifier.fillMaxSize(),color=KraPivaColors.Ink) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {TextButton({still=true}){Text(if(still)"Анимация остановлена" else "Пропустить анимацию")}}
            Spacer(Modifier.height(24.dp))
            ClubBeer(Modifier.widthIn(max=280.dp).fillMaxWidth(.7f).height(280.dp),still=still)
            ClubBrand()
            Text("Твоя пивная история",style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
            Text("Вкус. Впечатления. Люди.",color=KraPivaColors.Amber,textAlign=TextAlign.Center)
            LinearProgressIndicator(Modifier.widthIn(max=240.dp).fillMaxWidth())
            Text("Восстанавливаем сессию",color=KraPivaColors.Muted)
            Text("18+ · Крафтовое сообщество",style=MaterialTheme.typography.bodySmall,color=KraPivaColors.Muted)
        }
    }
}
