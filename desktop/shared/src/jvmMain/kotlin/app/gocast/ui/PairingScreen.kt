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

private val PairingFieldShape = RoundedCornerShape(8.dp)
private val FieldBorder = Color(0xFF2A3142)
private val FieldText = Color(0xFFE8ECF5)
private val FieldMuted = Color(0xFF7E8798)
private val BrandBlue = Color(0xFF6EA8FE)

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
                shape = PairingFieldShape,
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
                    focusedTextColor = FieldText,
                    unfocusedTextColor = FieldText,
                    cursorColor = BrandBlue,
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = FieldBorder,
                    focusedLabelColor = Color(0xFF9AA3B5),
                    unfocusedLabelColor = FieldMuted,
                    focusedTrailingIconColor = Color(0xFF9AA3B5),
                    unfocusedTrailingIconColor = FieldMuted,
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

/** Short code styled like the rename field — quiet outline, same corner radius. */
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
            color = FieldMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(PairingFieldShape)
                .border(width = 1.dp, color = FieldBorder, shape = PairingFieldShape)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = formatted,
                color = FieldText,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Classic pencil/edit mark — filled body + tip, readable at 18dp. */
@Composable
private fun EditGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.minDimension
        // Shaft (parallelogram)
        val shaft = Path().apply {
            moveTo(w * 0.28f, w * 0.72f)
            lineTo(w * 0.68f, w * 0.32f)
            lineTo(w * 0.78f, w * 0.42f)
            lineTo(w * 0.38f, w * 0.82f)
            close()
        }
        drawPath(shaft, color = tint)

        // Tip
        val tip = Path().apply {
            moveTo(w * 0.28f, w * 0.72f)
            lineTo(w * 0.14f, w * 0.86f)
            lineTo(w * 0.38f, w * 0.82f)
            close()
        }
        drawPath(tip, color = tint)

        // Eraser end band
        drawLine(
            color = tint,
            start = Offset(w * 0.62f, w * 0.26f),
            end = Offset(w * 0.74f, w * 0.38f),
            strokeWidth = w * 0.10f,
            cap = StrokeCap.Round,
        )

        // Lead notch on tip (cuts into fill for definition)
        drawLine(
            color = Color(0xFF12141A),
            start = Offset(w * 0.22f, w * 0.78f),
            end = Offset(w * 0.18f, w * 0.82f),
            strokeWidth = w * 0.06f,
            cap = StrokeCap.Round,
        )
    }
}
