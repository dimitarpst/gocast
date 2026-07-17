package app.gocast.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gocast.R
import app.gocast.capture.CaptureConsent
import app.gocast.cast.CastEvent
import app.gocast.cast.CastViewModel
import app.gocast.domain.ConnectionState
import app.gocast.domain.JoinUrlParser
import app.gocast.ui.quality.QualitySettingsScreen
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

@Composable
fun ConnectScreen(
    deepLinkUri: Uri?,
    resumeCastRequestId: Int = 0,
    viewModel: CastViewModel = viewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    var showQualitySettings by remember { mutableStateOf(false) }

    if (showQualitySettings) {
        QualitySettingsScreen(onBack = { showQualitySettings = false })
        return
    }

    LaunchedEffect(connectionState) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (connectionState is ConnectionState.Connected) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onCaptureConsentResult(result.resultCode, result.data)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore result — continue either way */ }

    val scanInvalidMessage = stringResource(R.string.scan_qr_invalid)
    val cameraDeniedMessage = stringResource(R.string.scan_qr_camera_denied)

    val qrScanner = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val raw = result.content.rawValue
                val uri = raw?.let { Uri.parse(it) }
                if (JoinUrlParser.parse(uri) == null) {
                    Toast.makeText(context, scanInvalidMessage, Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                viewModel.onDeepLink(uri)
            }
            is QRResult.QRMissingPermission -> {
                Toast.makeText(context, cameraDeniedMessage, Toast.LENGTH_SHORT).show()
            }
            is QRResult.QRUserCanceled,
            is QRResult.QRError,
            -> Unit
        }
    }

    val scannerConfig = remember {
        ScannerConfig.build {
            setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE))
            setOverlayStringRes(R.string.scan_qr_hint)
            setHapticSuccessFeedback(true)
            setShowTorchToggle(true)
            setShowCloseButton(true)
        }
    }

    // Collect consent events before resume/deeplink so a one-shot resume tap
    // is not lost if it emits before the collector starts.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CastEvent.RequestCaptureConsent -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    captureLauncher.launch(CaptureConsent.createIntent(context))
                }
            }
        }
    }

    LaunchedEffect(deepLinkUri) {
        // Resume opens MainActivity with no join URI — do not treat that as "stop".
        if (deepLinkUri != null) {
            viewModel.onDeepLink(deepLinkUri)
        }
    }

    LaunchedEffect(resumeCastRequestId) {
        if (resumeCastRequestId > 0) {
            viewModel.onResumeCastRequested()
        }
    }

    val label = when (val state = connectionState) {
        is ConnectionState.Idle -> "Scan QR to connect"
        is ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Connected -> "Connected!"
        is ConnectionState.Reconnecting -> "Reconnecting..."
        is ConnectionState.Failed -> state.reason
    }

    val canScan = connectionState is ConnectionState.Idle ||
        connectionState is ConnectionState.Failed

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (canScan) {
            Button(onClick = { qrScanner.launch(scannerConfig) }) {
                Text(stringResource(R.string.scan_qr))
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { showQualitySettings = true }) {
            Text("Quality settings")
        }
    }
}
