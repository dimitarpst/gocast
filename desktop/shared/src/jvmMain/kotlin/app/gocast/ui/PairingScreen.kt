package app.gocast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.domain.model.JoinSession
import app.gocast.qr.QrCodeEncoder

@Composable
fun PairingScreen(
    session: JoinSession,
    displayName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(session.joinUrl) {
        QrCodeEncoder.encode(session.joinUrl)
    }
    var draftName by remember(displayName) { mutableStateOf(displayName) }
    val focusManager = LocalFocusManager.current

    fun commitName() {
        val next = draftName.trim()
        if (next.isNotEmpty() && next != displayName) {
            onRename(next)
        } else {
            draftName = displayName
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141A))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // Top band — brand + name live here, not in the QR stack.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandHeader(logoHeight = 36.dp)
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it.take(48) },
                singleLine = true,
                label = { Text("This PC") },
                trailingIcon = {
                    EditGlyph(
                        tint = Color(0xFF9AA3B5),
                        modifier = Modifier.size(18.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitName()
                        focusManager.clearFocus()
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFE8ECF5),
                    unfocusedTextColor = Color(0xFFE8ECF5),
                    cursorColor = Color(0xFF6EA8FE),
                    focusedBorderColor = Color(0xFF6EA8FE),
                    unfocusedBorderColor = Color(0xFF2A3142),
                    focusedLabelColor = Color(0xFF9AA3B5),
                    unfocusedLabelColor = Color(0xFF7E8798),
                    focusedTrailingIconColor = Color(0xFF9AA3B5),
                    unfocusedTrailingIconColor = Color(0xFF7E8798),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .onFocusChanged { state ->
                        if (!state.isFocused) commitName()
                    },
            )
        }

        // Stage — only join affordances, centered in the leftover space.
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
            }
        }
    }
}

/** Short code as a clear alternate join path — soft brand frame, not plain text. */
@Composable
private fun PairingCodeHighlight(
    code: String,
    modifier: Modifier = Modifier,
) {
    val formatted = code.chunked(2).joinToString("  ")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Or enter code",
            color = Color(0xFF7E8798),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x1473ACFE))
                .border(
                    width = 1.dp,
                    color = Color(0x5573ACFE),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Text(
                text = formatted,
                color = Color(0xFFE8ECF5),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EditGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.minDimension
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        // Pencil body
        val path = Path().apply {
            moveTo(w * 0.22f, w * 0.72f)
            lineTo(w * 0.68f, w * 0.26f)
            lineTo(w * 0.78f, w * 0.36f)
            lineTo(w * 0.32f, w * 0.82f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
        // Tip
        drawLine(
            color = tint,
            start = Offset(w * 0.18f, w * 0.78f),
            end = Offset(w * 0.28f, w * 0.88f),
            strokeWidth = w * 0.11f,
            cap = StrokeCap.Round,
        )
        // Cap line
        drawLine(
            color = tint,
            start = Offset(w * 0.58f, w * 0.22f),
            end = Offset(w * 0.72f, w * 0.36f),
            strokeWidth = w * 0.11f,
            cap = StrokeCap.Round,
        )
    }
}
