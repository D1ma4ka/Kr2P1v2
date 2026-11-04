package ru.krapiva.app.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.krapiva.app.R
import ru.krapiva.app.designsystem.KraPivaColors
import ru.krapiva.app.designsystem.KraPivaSpacing

@Composable
fun WelcomeScreen() {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(KraPivaSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(KraPivaSpacing.Large),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.brand_eyebrow), style = MaterialTheme.typography.labelSmall,
                        color = KraPivaColors.Amber, modifier = Modifier.weight(1f))
                    Surface(color = KraPivaColors.Surface, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.adult_label), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null,
                    modifier = Modifier.size(144.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                    color = KraPivaColors.Amber)
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge,
                    color = KraPivaColors.Muted)
                HorizontalDivider(color = KraPivaColors.Muted.copy(alpha = 0.3f))
                Text(stringResource(R.string.brand_signature), style = MaterialTheme.typography.labelSmall,
                    color = KraPivaColors.Amber)
                Button(onClick = { showAbout = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.about_action))
                }
                Text(stringResource(R.string.responsible_note), style = MaterialTheme.typography.bodyMedium,
                    color = KraPivaColors.Muted)
            }
        }
    }
    if (showAbout) {
        AlertDialog(onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_body),
                modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.close_action))
                }
            })
    }
}
