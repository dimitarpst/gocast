package app.gocast.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gocast.R
import app.gocast.capture.CaptureConsent
import app.gocast.cast.CastEvent
import app.gocast.cast.CastViewModel
import app.gocast.domain.ConnectionState
import app.gocast.domain.DiscoveredHost
import app.gocast.domain.JoinParams
import app.gocast.domain.JoinUrlParser
import app.gocast.domain.TrustedPc
import app.gocast.ui.components.DiscoveredHostCard
import app.gocast.ui.components.LastConnectedCard
import app.gocast.ui.quality.QualitySettingsScreen
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

private enum class ConnectMode {
    Home,
    Discover,
    EnterCode,
    Quality,
}

@Composable
fun ConnectScreen(
    deepLinkUri: Uri?,
    resumeCastRequestId: Int = 0,
    viewModel: CastViewModel = viewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val trustedPc by viewModel.trustedPc.collectAsStateWithLifecycle()
    val discoveredHosts by viewModel.discoveredHosts.collectAsStateWithLifecycle()
    val joinBusy by viewModel.joinBusy.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ConnectMode.Home) }
    var selectedHost by remember { mutableStateOf<DiscoveredHost?>(null) }
    var codeInput by remember { mutableStateOf("") }

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

    // Keep collectors alive even on Quality settings so Resume always works.
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
        if (deepLinkUri != null) {
            viewModel.onDeepLink(deepLinkUri)
        }
    }

    LaunchedEffect(resumeCastRequestId) {
        if (resumeCastRequestId > 0) {
            // Leave settings / discovery so the consent dialog can show on Home.
            mode = ConnectMode.Home
            selectedHost = null
            codeInput = ""
            viewModel.stopDiscovery()
            viewModel.onResumeCastRequested()
        }
    }

    LaunchedEffect(connectionState) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (connectionState is ConnectionState.Connected) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (mode == ConnectMode.Discover || mode == ConnectMode.EnterCode) {
                mode = ConnectMode.Home
                viewModel.stopDiscovery()
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(mode) {
        if (mode == ConnectMode.Discover) {
            viewModel.startDiscovery()
        } else if (mode != ConnectMode.EnterCode) {
            viewModel.stopDiscovery()
        }
    }

    BackHandler(enabled = mode != ConnectMode.Home) {
        when (mode) {
            ConnectMode.Quality -> mode = ConnectMode.Home
            ConnectMode.Discover,
            ConnectMode.EnterCode,
            -> {
                viewModel.stopDiscovery()
                selectedHost = null
                codeInput = ""
                mode = ConnectMode.Home
            }
            ConnectMode.Home -> Unit
        }
    }

    if (mode == ConnectMode.Quality) {
        QualitySettingsScreen(onBack = { mode = ConnectMode.Home })
        return
    }

    val label = when (val state = connectionState) {
        is ConnectionState.Idle -> stringResource(R.string.connect_idle)
        is ConnectionState.Connecting -> stringResource(R.string.connecting)
        is ConnectionState.Connected -> stringResource(R.string.connected)
        is ConnectionState.Reconnecting -> stringResource(R.string.reconnecting)
        is ConnectionState.Failed -> state.reason
    }

    val canJoin = connectionState is ConnectionState.Idle ||
        connectionState is ConnectionState.Failed
    val isActiveCast = connectionState is ConnectionState.Connected ||
        connectionState is ConnectionState.Reconnecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            ConnectMode.Home -> HomeContent(
                label = label,
                connectionState = connectionState,
                trustedPc = trustedPc,
                canJoin = canJoin,
                isActiveCast = isActiveCast,
                joinBusy = joinBusy,
                onCastTrusted = { viewModel.castToTrustedPc() },
                onScanQr = { qrScanner.launch(scannerConfig) },
                onFindWifi = { mode = ConnectMode.Discover },
                onStop = { viewModel.stopCast() },
                onQuality = { mode = ConnectMode.Quality },
            )

            ConnectMode.Discover -> DiscoverContent(
                hosts = discoveredHosts,
                onHostClick = { host ->
                    selectedHost = host
                    codeInput = ""
                    mode = ConnectMode.EnterCode
                },
                onBack = {
                    viewModel.stopDiscovery()
                    mode = ConnectMode.Home
                },
            )

            ConnectMode.EnterCode -> EnterCodeContent(
                host = selectedHost,
                codeInput = codeInput,
                onCodeChange = { raw ->
                    codeInput = raw.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(4)
                },
                canJoin = canJoin,
                onConnect = {
                    val target = selectedHost ?: return@EnterCodeContent
                    if (codeInput.length < 4) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.code_too_short),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@EnterCodeContent
                    }
                    viewModel.stopDiscovery()
                    viewModel.startJoin(
                        JoinParams(
                            host = target.host,
                            port = target.port,
                            token = codeInput,
                        ),
                    )
                },
                onBack = {
                    codeInput = ""
                    mode = ConnectMode.Discover
                },
            )

            ConnectMode.Quality -> Unit
        }
    }
}

@Composable
private fun HomeContent(
    label: String,
    connectionState: ConnectionState,
    trustedPc: TrustedPc?,
    canJoin: Boolean,
    isActiveCast: Boolean,
    joinBusy: Boolean,
    onCastTrusted: () -> Unit,
    onScanQr: () -> Unit,
    onFindWifi: () -> Unit,
    onStop: () -> Unit,
    onQuality: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.gocast_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(48.dp),
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = label,
            style = if (connectionState is ConnectionState.Idle) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineSmall
            },
            fontWeight = if (connectionState is ConnectionState.Idle) {
                FontWeight.Medium
            } else {
                FontWeight.SemiBold
            },
            color = when (connectionState) {
                is ConnectionState.Failed -> MaterialTheme.colorScheme.error
                is ConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        if (isActiveCast) {
            ActiveCastCard(
                displayName = trustedPc?.displayName,
                onStop = onStop,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.quality_live_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }

        if (canJoin) {
            trustedPc?.let { pc ->
                LastConnectedCard(
                    pc = pc,
                    onClick = onCastTrusted,
                    busy = joinBusy,
                    enabled = !joinBusy && (
                        connectionState is ConnectionState.Idle ||
                            connectionState is ConnectionState.Failed
                        ),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.other_ways),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scan_qr))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onFindWifi,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.find_on_wifi))
            }
            Spacer(Modifier.weight(1f))
        }

        TextButton(onClick = onQuality) {
            Text(stringResource(R.string.quality_settings))
        }
    }
}

@Composable
private fun ActiveCastCard(
    displayName: String?,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (displayName != null) {
                Text(
                    text = stringResource(R.string.casting_to, displayName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.stop_cast))
            }
        }
    }
}

@Composable
private fun DiscoverContent(
    hosts: List<DiscoveredHost>,
    onHostClick: (DiscoveredHost) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.find_on_wifi),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.looking_for_pcs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (hosts.isEmpty()) {
            Text(
                text = stringResource(R.string.no_pcs_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(hosts, key = { it.instanceId }) { host ->
                    DiscoveredHostCard(
                        host = host,
                        onClick = { onHostClick(host) },
                    )
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun EnterCodeContent(
    host: DiscoveredHost?,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    canJoin: Boolean,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.enter_code),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (host != null) {
                stringResource(R.string.enter_code_for, host.displayName)
            } else {
                stringResource(R.string.pairing_code)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.pairing_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConnect,
            enabled = canJoin && host != null && codeInput.length == 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.connect))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.back))
        }
    }
}
