package app.gocast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.R
import app.gocast.update.PhoneUpdateState

/** A newer GoCast on GitHub: one tap downloads it and opens Android's installer. */
@Composable
fun UpdateCard(
    state: PhoneUpdateState,
    onUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = when (state) {
        PhoneUpdateState.None -> return
        is PhoneUpdateState.Available -> state.update
        is PhoneUpdateState.Downloading -> state.update
        is PhoneUpdateState.NeedsPermission -> state.update
        is PhoneUpdateState.Failed -> state.update
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.update_available, update.version),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            when (state) {
                is PhoneUpdateState.Downloading -> {
                    Text(
                        stringResource(R.string.update_downloading, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is PhoneUpdateState.NeedsPermission -> {
                    Text(
                        stringResource(R.string.update_allow_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    UpdateButton(stringResource(R.string.update_allow), onAllowInstalls)
                }
                is PhoneUpdateState.Failed -> {
                    Text(
                        stringResource(R.string.update_failed, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    UpdateButton(stringResource(R.string.update_retry), onUpdate)
                }
                else -> UpdateButton(stringResource(R.string.update_button), onUpdate)
            }
        }
    }
}

@Composable
private fun UpdateButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}
