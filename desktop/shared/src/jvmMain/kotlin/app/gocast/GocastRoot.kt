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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.gocast.discovery.GocastAdvertiser
import app.gocast.domain.model.JoinSession
import app.gocast.platform.AppRelaunch
import app.gocast.platform.GocastDataDirs
import app.gocast.platform.WindowsLanAccess
import app.gocast.server.SessionGuard
import app.gocast.trust.DesktopIdentityStore
import app.gocast.ui.BrandHeader
import app.gocast.ui.LanSetupScreen
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cef.callback.CefCommandLine
import kotlin.math.max

@Composable
fun GocastRoot(
    session: JoinSession,
    identityStore: DesktopIdentityStore,
    advertiser: GocastAdvertiser,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    var kcefReady by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var restartRequired by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf(session.displayName) }
    // Packaged Windows with no firewall rule: gate QR until LAN setup finishes.
    var lanReady by remember {
        mutableStateOf(WindowsLanAccess.status() != WindowsLanAccess.Status.Blocked)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val bundleDir = GocastDataDirs.kcefBundle()
                val cacheDir = GocastDataDirs.kcefCache()
                KCEF.init(
                    builder = {
                        installDir(bundleDir)
                        // addArgs() is ignored by many KCEF/JCEF builds — set the
                        // Chromium switch in onBeforeCommandLineProcessing instead.
                        // Without this, unmuted <audio> throws NotAllowedError until
                        // the user clicks the volume slider (a "user gesture").
                        appHandler(
                            object : KCEF.AppHandler() {
                                override fun onBeforeCommandLineProcessing(
                                    process_type: String?,
                                    command_line: CefCommandLine?,
                                ) {
                                    super.onBeforeCommandLineProcessing(process_type, command_line)
                                    command_line?.appendSwitchWithValue(
                                        "autoplay-policy",
                                        "no-user-gesture-required",
                                    )
                                }
                            },
                        )
                        progress {
                            onDownloading {
                                downloadProgress = max(it, 0f)
                            }
                            onInitialized {
                                kcefReady = true
                            }
                        }
                        settings {
                            cachePath = cacheDir.absolutePath
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

    // Do not disposeBlocking() here — it freezes the window close button for seconds.
    // Shutdown is handled from main.kt on a background thread.

    MaterialTheme {
        when {
            restartRequired -> {
                RestartRequiredScreen(
                    canRelaunch = AppRelaunch.canRelaunchPackagedApp(),
                    onRestart = { AppRelaunch.relaunchPackagedAppOrExit() },
                )
            }
            initError != null -> {
                StartupMessage("Viewer engine failed: $initError")
            }
            !kcefReady -> {
                ViewerLoadingScreen(progressPercent = downloadProgress)
            }
            !lanReady -> {
                LanSetupScreen(onReady = { lanReady = true })
            }
            else -> App(
                session = session,
                displayName = displayName,
                onRename = { next ->
                    val updated = identityStore.updateDisplayName(next)
                    displayName = updated.displayName
                    SessionGuard.displayName = updated.displayName
                    advertiser.start(session.port, session.instanceId, updated.displayName)
                },
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onExitFullscreen = onExitFullscreen,
            )
        }
    }
}

@Composable
private fun RestartRequiredScreen(
    canRelaunch: Boolean,
    onRestart: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12141A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(32.dp),
        ) {
            Text(
                text = "Viewer download finished",
                color = Color(0xFFE8ECF5),
                fontSize = 18.sp,
            )
            Text(
                text = if (canRelaunch) {
                    "Restart once to load the built-in viewer."
                } else {
                    "Close gocast and open it again to load the built-in viewer."
                },
                color = Color(0xFF9AA3B5),
                fontSize = 14.sp,
            )
            if (canRelaunch) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6EA8FE),
                        contentColor = Color(0xFF0E1016),
                    ),
                ) {
                    Text("Restart gocast")
                }
            }
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
