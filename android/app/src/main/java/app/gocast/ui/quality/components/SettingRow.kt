package app.gocast.ui.quality.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gocast.settings.CostChip
import app.gocast.settings.SettingHelp

@Composable
fun SettingRow(
    help: SettingHelp,
    onInfo: (SettingHelp) -> Unit,
    onCostTap: (CostChip) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CostChipRow(help.costs, onCostTap = onCostTap)
            IconButton(onClick = { onInfo(help) }) {
                Text(
                    "ⓘ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        content()
    }
}
