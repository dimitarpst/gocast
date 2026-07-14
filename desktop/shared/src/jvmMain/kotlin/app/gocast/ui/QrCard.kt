package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun QrCard(session: JoinSession){
    val qrBitmap = remember(session.joinUrl){
        QrCodeEncoder.encode(session.joinUrl)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp).fillMaxSize(),
    ){
        Image(
            bitmap = qrBitmap,
            contentDescription = "QR code to join session",
            modifier = Modifier.size(256.dp),
        )
        Text(
            text = session.joinUrl,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}