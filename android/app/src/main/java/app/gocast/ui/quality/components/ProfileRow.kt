package app.gocast.ui.quality.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gocast.settings.CastQualityProfile
import app.gocast.settings.CastQualitySettings

/** One profile in the Profile group; the chosen one gets a check mark. */
@Composable
fun ProfileRow(
    profile: CastQualityProfile,
    selected: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val description = when (profile.id) {
        CastQualityProfile.ID_SMOOTH -> "Fluid motion"
        CastQualityProfile.ID_BALANCED -> "Everyday default"
        CastQualityProfile.ID_SHARP -> "Clearest text"
        else -> profileSummary(profile.settings)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                if (recommended) {
                    Text(
                        "Recommended",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${profile.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun profileSummary(s: CastQualitySettings): String {
    val scale = "${(s.resolutionScale * 100).toInt()}%"
    val maxMb = s.maxBitrateBps / 1_000_000
    return "$scale · ${s.maxFramerate} fps · ${maxMb} Mbps · ${s.preferredCodec.name}"
}
