package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.domain.model.JoinSession
import app.gocast.qr.QrCodeEncoder

@Composable
fun PairingScreen(
    session: JoinSession,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(session.joinUrl) {
        QrCodeEncoder.encode(session.joinUrl)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141A))
            .padding(32.dp),
    ) {
        BrandHeader(logoHeight = 52.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Scan to cast",
            color = Color(0xFFE8ECF5),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "or type this code on your phone",
            color = Color(0xFF9AA3B5),
            fontSize = 16.sp,
        )

        Spacer(Modifier.height(32.dp))

        Image(
            bitmap = qrBitmap,
            contentDescription = "QR code to join session",
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = session.shortCode.chunked(2).joinToString(" "),
            color = Color(0xFFE8ECF5),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
        )
    }
}
