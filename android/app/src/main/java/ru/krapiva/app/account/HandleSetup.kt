package ru.krapiva.app.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun HandleEntry(state: AccountState, model: AccountViewModel) {
    var handle by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Выбери уникальный никнейм. Отображаемое имя можно менять отдельно.")
        OutlinedTextField(handle, {handle=it}, label={Text("Никнейм")}, singleLine=true, enabled=!state.busy, modifier=Modifier.fillMaxWidth())
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button({model.claimHandle(handle)},enabled=!state.busy&&UserHandle.valid(handle)) {Text("Сохранить никнейм")}
    }
}

/** A legacy account remains fully accessible through Later, including its existing drafts. */
@Composable internal fun HandleSetup(state: AccountState, model: AccountViewModel) {
    val profile=state.profile ?: return
    var dismissed by rememberSaveable(profile.getString("userId")) { mutableStateOf(false) }
    if (!dismissed && (profile.isNull("handle") || profile.optString("handle").isBlank())) {
        AlertDialog(onDismissRequest={dismissed=true},title={Text("Твой никнейм")},
            text={HandleEntry(state,model)},confirmButton={},
            dismissButton={TextButton({dismissed=true}){Text("Позже")}})
    }
}
