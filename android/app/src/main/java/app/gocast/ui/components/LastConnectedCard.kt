package app.gocast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gocast.R
import app.gocast.domain.TrustedPc

@Composable
fun LastConnectedCard(
    pc: TrustedPc,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.last_connected),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = pc.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 72.dp),
                ) {
                    Text(
                        text = pc.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                    Text(
                        text = if (busy) {
                            stringResource(R.string.connecting)
                        } else {
                            stringResource(R.string.tap_to_cast)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
