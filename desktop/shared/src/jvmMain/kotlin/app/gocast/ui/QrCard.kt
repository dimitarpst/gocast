package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gocast.domain.model.JoinSession
import app.gocast.qr.QrCodeEncoder

@Composable
fun QrCard(
    session: JoinSession,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(session.joinUrl) {
        QrCodeEncoder.encode(session.joinUrl)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            text = "Scan to cast",
            style = MaterialTheme.typography.titleMedium,
        )
        Image(
            bitmap = qrBitmap,
            contentDescription = "QR code to join session",
            modifier = Modifier.padding(top = 16.dp).size(220.dp),
        )
        SelectionContainer {
            Text(
                text = session.joinUrl,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
