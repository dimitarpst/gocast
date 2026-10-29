package app.gocast.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.gocast.protocol.RelayError
import app.gocast.protocol.RelayState
import app.gocast.server.RelayAccess
import kotlinx.coroutines.launch

/**
 * Relay lock for this PC: an Unlock button while locked, a quiet label when on or
 * switched off, nothing while the signaling server is unreachable.
 */
@Composable
fun RelayControl() {
    val status by RelayAccess.status.collectAsState()
    var visible by remember { mutableStateOf(false) }
    when (status?.state) {
        RelayState.Locked -> TextButton(onClick = { visible = true }) {
            Text("Unlock relay", style = MaterialTheme.typography.bodySmall)
        }
        RelayState.On -> RelayLabel("Relay on")
        RelayState.Off -> RelayLabel("Relay off")
        null -> Unit
    }
    if (visible) UnlockRelayDialog(onDismiss = { visible = false })
}

@Composable
private fun RelayLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun UnlockRelayDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun unlock() {
        if (password.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            val reply = RelayAccess.unlock?.invoke(password)
            busy = false
            when {
                reply == null -> error = "Couldn't reach the GoCast server. Check your connection and try again."
                reply.state == RelayState.On -> onDismiss()
                reply.error == RelayError.TooManyAttempts -> error = "Too many tries. Wait an hour, then try again."
                reply.error == RelayError.WrongPassword -> error = "That password isn't right."
                else -> error = "The relay is switched off on the server right now."
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else false
        },
        title = { Text("Unlock the relay") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                label = { Text("Relay password") },
                shape = MaterialTheme.shapes.large,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(error ?: "Lets your phone reach this PC from any network. You only need to do this once per PC.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { unlock() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            Button(onClick = { unlock() }, enabled = password.isNotBlank() && !busy) {
                Text(if (busy) "Checking…" else "Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
