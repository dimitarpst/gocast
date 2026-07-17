package app.gocast.ui.quality.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.settings.CostChip
import app.gocast.settings.CostWeight

@Composable
fun CostChipRow(
    costs: List<CostChip>,
    onCostTap: (CostChip) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        costs.forEach { chip ->
            Text(
                text = chip.axisLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = costColor(chip.weight),
                modifier = Modifier.clickable { onCostTap(chip) },
            )
        }
    }
}

@Composable
fun costColor(weight: CostWeight): Color {
    return when (weight) {
        CostWeight.LIGHT -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        CostWeight.NOTICEABLE -> MaterialTheme.colorScheme.tertiary
        CostWeight.HEAVY -> MaterialTheme.colorScheme.error
    }
}
