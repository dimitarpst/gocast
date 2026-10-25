package app.gocast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.R

enum class ConnectionActionSymbol { Qr, Wifi }

/** A single, clearly labelled tap target for an existing connection action. */
@Composable
fun ConnectionActionCard(
    title: String,
    subtitle: String,
    symbol: ConnectionActionSymbol,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val foreground = if (prominent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (prominent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = foreground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 112.dp)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(foreground.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (symbol) {
                    ConnectionActionSymbol.Qr -> Icon(
                        painter = painterResource(R.drawable.ic_qr_code),
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(26.dp),
                    )
                    ConnectionActionSymbol.Wifi -> Canvas(Modifier.size(26.dp)) {
                        drawWifiSymbol(foreground)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (prominent) foreground.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(Modifier.size(18.dp)) {
                val arrow = Path().apply {
                    moveTo(size.width * 0.35f, size.height * 0.2f)
                    lineTo(size.width * 0.65f, size.height * 0.5f)
                    lineTo(size.width * 0.35f, size.height * 0.8f)
                }
                drawPath(arrow, foreground, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawWifiSymbol(color: Color) {
    val unit = size.minDimension / 24f
    val line = Stroke(width = 1.8f * unit, cap = StrokeCap.Round)
    val source = Offset(12f * unit, 19f * unit)
    // Concentric bands radiate from the dot, with equal spacing between them.
    listOf(14f, 9.5f, 5f).forEach { radius ->
        val radiusPx = radius * unit
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = source - Offset(radiusPx, radiusPx),
            size = Size(radiusPx * 2f, radiusPx * 2f),
            style = line,
        )
    }
    drawCircle(color, radius = 1.5f * unit, center = source)
}
