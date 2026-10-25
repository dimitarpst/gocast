package app.gocast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.platform.WindowsLanAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BrandBlue = Color(0xFF6EA8FE)

private enum class LanSetupPhase {
    Intro,
    Waiting,
    Success,
    Failed,
}

/**
 * Required first-run gate on packaged Windows before QR pairing.
 * Always shown once after a fresh install; UAC only when firewall rules are missing.
 */
@Composable
fun LanSetupScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rulesAlreadyOk = remember {
        WindowsLanAccess.status() == WindowsLanAccess.Status.Allowed
    }
    var phase by remember {
        mutableStateOf(if (rulesAlreadyOk) LanSetupPhase.Success else LanSetupPhase.Intro)
    }
    val scope = rememberCoroutineScope()

    fun finish() {
        WindowsLanAccess.markFirstRunCompleted()
        onReady()
    }

    fun startAllow() {
        scope.launch {
            phase = LanSetupPhase.Waiting
            val status = withContext(Dispatchers.IO) {
                WindowsLanAccess.requestAllowNow()
            }
            phase = if (status == WindowsLanAccess.Status.Allowed) {
                LanSetupPhase.Success
            } else {
                LanSetupPhase.Failed
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12141A), Color(0xFF1B2030), Color(0xFF0E1016)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(32.dp),
        ) {
            BrandHeader(logoHeight = 44.dp)
            Spacer(Modifier.height(28.dp))

            when (phase) {
                LanSetupPhase.Intro -> {
                    Text(
                        text = "Allow phone connections",
                        color = Color(0xFFE8ECF5),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Your phone connects to this PC over Wi-Fi. " +
                            "Windows needs permission so gocast can accept that connection " +
                            "on your network (port 8765).",
                        color = Color(0xFF9AA3B5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You'll see a Windows security prompt — choose Yes. " +
                            "gocast opens LAN access on both Private and Public networks " +
                            "(home Wi‑Fi is often marked Public).",
                        color = Color(0xFF7E8798),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { startAllow() },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color(0xFF0E1016),
                        ),
                    ) {
                        Text("Allow phone connections")
                    }
                }

                LanSetupPhase.Waiting -> {
                    Text(
                        text = "Waiting for Windows…",
                        color = Color(0xFFE8ECF5),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Approve the prompt if it appears. This only opens LAN access for gocast.",
                        color = Color(0xFF9AA3B5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                LanSetupPhase.Success -> {
                    Text(
                        text = if (rulesAlreadyOk) "Network access is ready" else "You're set",
                        color = Color(0xFFE8ECF5),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "This PC can accept casts from your phone on the same Wi-Fi. " +
                            "Next you'll see a QR code and a short code to join.",
                        color = Color(0xFF9AA3B5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { finish() },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color(0xFF0E1016),
                        ),
                    ) {
                        Text("Continue")
                    }
                }

                LanSetupPhase.Failed -> {
                    Text(
                        text = "Permission wasn't granted",
                        color = Color(0xFFE8ECF5),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Without this, your phone will time out trying to reach the PC. " +
                            "Try again and choose Yes on the Windows prompt.",
                        color = Color(0xFF9AA3B5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { startAllow() },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlue,
                            contentColor = Color(0xFF0E1016),
                        ),
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
