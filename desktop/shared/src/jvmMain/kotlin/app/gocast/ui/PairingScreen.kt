package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.domain.ShortCodeGenerator
import app.gocast.domain.model.JoinSession
import app.gocast.qr.QrCodeEncoder

private val FieldBorder = Color(0xFF2A3142)
private val FieldText = Color(0xFFE8ECF5)
private val FieldMuted = Color(0xFF7E8798)
private val CodeBoxShape = RoundedCornerShape(16.dp)

@Composable
fun PairingScreen(
    session: JoinSession,
    displayName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    canInstallUpdate: Boolean = true,
) {
    val qrBitmap = remember(session.joinUrl) {
        QrCodeEncoder.encode(session.joinUrl)
    }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141A))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 720.dp
            val nameHeader = @Composable {
                PcNameHeader(
                    displayName = displayName,
                    onRenameClick = { showRenameDialog = true },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }

            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BrandHeader(logoHeight = 36.dp)
                    nameHeader()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BrandHeader(logoHeight = 36.dp)
                    nameHeader()
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR code to join session",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(320.dp),
                )
                Spacer(modifier = Modifier.height(28.dp))
                PairingCodeHighlight(code = session.shortCode)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${session.host}:${session.port}",
                    color = FieldMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DesktopUpdateControl(canInstall = canInstallUpdate)
        }
    }
    // Keep dialog state outside the responsive header branches when resizing.
    if (showRenameDialog) {
        RenamePcDialog(
            currentName = displayName,
            onSave = onRename,
            onDismiss = { showRenameDialog = false },
        )
    }
}

/**
 * Short code as one continuous Epic-style group of tall digit boxes
 * (no mid-code gap for 4-character pairing codes).
 */
@Composable
private fun PairingCodeHighlight(
    code: String,
    modifier: Modifier = Modifier,
) {
    val chars = code.uppercase().take(ShortCodeGenerator.LENGTH).padEnd(ShortCodeGenerator.LENGTH)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Or enter code",
            color = FieldMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chars.forEach { ch ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(52.dp)
                        .height(68.dp)
                        .clip(CodeBoxShape)
                        .border(width = 1.dp, color = FieldBorder, shape = CodeBoxShape),
                ) {
                    Text(
                        text = if (ch.isWhitespace()) "" else ch.toString(),
                        color = FieldText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
