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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gocast.R

enum class ConnectionActionSymbol { Qr, Wifi, Bluetooth }

/** A single, clearly labelled tap target for an existing connection action. */
@Composable
fun ConnectionActionCard(
    title: String,
    subtitle: String,
    symbol: ConnectionActionSymbol,
    prominent: Boolean,
    onClick: () -> Unit,
    /** Notification-style count on the symbol, e.g. PCs already found. Hidden at 0. */
    badgeCount: Int = 0,
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
            BadgedBox(
                badge = {
                    if (badgeCount > 0) Badge { Text(badgeCount.toString()) }
                },
            ) {
                SymbolCircle(symbol, foreground)
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

@Composable
private fun SymbolCircle(symbol: ConnectionActionSymbol, foreground: Color) {
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
            ConnectionActionSymbol.Bluetooth -> Canvas(Modifier.size(26.dp)) {
                drawBluetoothSymbol(foreground)
            }
        }
    }
}

/** The Bluetooth rune: a spine, two arrow-heads and the crossing strokes. */
internal fun DrawScope.drawBluetoothSymbol(color: Color) {
    val unit = size.minDimension / 24f
    val rune = Path().apply {
        moveTo(6.5f * unit, 7f * unit)
        lineTo(17f * unit, 16.5f * unit)
        lineTo(12f * unit, 21.5f * unit)
        lineTo(12f * unit, 2.5f * unit)
        lineTo(17f * unit, 7.5f * unit)
        lineTo(6.5f * unit, 17f * unit)
    }
    drawPath(
        rune,
        color,
        style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** The approved Wi‑Fi symbol. [bandAlpha] gets 0 for the inner band up to 2 for the outer one. */
internal fun DrawScope.drawWifiSymbol(color: Color, bandAlpha: (Int) -> Float = { 1f }) {
    val unit = size.minDimension / 24f
    val line = Stroke(width = 1.8f * unit, cap = StrokeCap.Round)
    val source = Offset(12f * unit, 19f * unit)
    // Concentric bands radiate from the dot, with equal spacing between them.
    listOf(14f, 9.5f, 5f).forEachIndexed { index, radius ->
        val radiusPx = radius * unit
        drawArc(
            color = color.copy(alpha = color.alpha * bandAlpha(2 - index)),
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
