package app.gocast

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gocast.domain.model.JoinSession
import app.gocast.ui.BrandHeader
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun GocastRoot(session: JoinSession) {
    var kcefReady by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var restartRequired by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                KCEF.init(
                    builder = {
                        installDir(File("kcef-bundle"))
                        progress {
                            onDownloading {
                                downloadProgress = max(it, 0f)
                            }
                            onInitialized {
                                kcefReady = true
                            }
                        }
                        settings {
                            cachePath = File("kcef-cache").absolutePath
                        }
                    },
                    onError = { error ->
                        initError = error?.message ?: "KCEF init failed"
                        error?.printStackTrace()
                    },
                    onRestartRequired = {
                        restartRequired = true
                    },
                )
            } catch (e: Exception) {
                initError = e.message ?: "KCEF init failed"
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            KCEF.disposeBlocking()
        }
    }

    MaterialTheme {
        when {
            restartRequired -> {
                StartupMessage("Restart gocast to finish the built-in viewer setup")
            }
            initError != null -> {
                StartupMessage("Viewer engine failed: $initError")
            }
            !kcefReady -> {
                ViewerLoadingScreen(progressPercent = downloadProgress)
            }
            else -> App(session)
        }
    }
}

@Composable
private fun ViewerLoadingScreen(progressPercent: Float) {
    val fraction = (progressPercent / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "kcefProgress",
    )

    Box(
        modifier = Modifier
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
                .widthIn(max = 420.dp)
                .padding(32.dp),
        ) {
            BrandHeader(logoHeight = 44.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Preparing built-in viewer",
                color = Color(0xFF9AA3B5),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(0xFF6EA8FE),
                trackColor = Color(0xFF2A3142),
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${progressPercent.toInt()}%",
                color = Color(0xFFC5CDDB),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "One-time download — next launches are instant",
                color = Color(0xFF7E8798),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StartupMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12141A)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFFE8ECF5))
    }
}
