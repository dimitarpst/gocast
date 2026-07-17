package app.gocast.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gocast.capture.CaptureConsent
import app.gocast.cast.CastEvent
import app.gocast.cast.CastViewModel
import app.gocast.domain.ConnectionState

@Composable
fun ConnectScreen(
    deepLinkUri: Uri?,
    viewModel: CastViewModel = viewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onCaptureConsentResult(result.resultCode, result.data)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore result — continue either way */ }

    LaunchedEffect(deepLinkUri) {
        viewModel.onDeepLink(deepLinkUri)
    }

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

    val label = when (val state = connectionState) {
        is ConnectionState.Idle -> "Scan QR to connect"
        is ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Connected -> "Connected!"
        is ConnectionState.Reconnecting -> "Reconnecting..."
        is ConnectionState.Failed -> state.reason
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}