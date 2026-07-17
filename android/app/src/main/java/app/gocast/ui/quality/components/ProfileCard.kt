package app.gocast.ui.quality.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.settings.CastQualityProfile
import app.gocast.settings.CastQualitySettings

@Composable
fun ProfileCard(
    profile: CastQualityProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val description = when (profile.id) {
        CastQualityProfile.ID_SMOOTH ->
            "Full resolution at 60 fps. Keeps motion fluid when Wi‑Fi dips."
        CastQualityProfile.ID_BALANCED ->
            "Native resolution, solid everyday default."
        CastQualityProfile.ID_SHARP ->
            "Maximum clarity — never blurs. Best for text and code."
        else -> profileSummary(profile.settings)
    }
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    if (profile.builtIn) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "built-in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun profileSummary(s: CastQualitySettings): String {
    val scale = "${(s.resolutionScale * 100).toInt()}%"
    val maxMb = s.maxBitrateBps / 1_000_000
    return "$scale · ${s.maxFramerate} fps · ${maxMb} Mbps · ${s.preferredCodec.name}"
}
