package app.gocast.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gocast.R
import app.gocast.capture.CaptureConsent
import app.gocast.capture.CaptureService
import app.gocast.cast.CastEvent
import app.gocast.cast.CastViewModel
import app.gocast.domain.ConnectionState
import app.gocast.domain.DiscoveredHost
import app.gocast.protocol.JoinParams
import app.gocast.domain.JoinUrlParser
import app.gocast.domain.TrustedPc
import app.gocast.network.CastLinkMonitor
import app.gocast.protocol.LinkType
import app.gocast.ui.components.DiscoveredHostCard
import app.gocast.ui.components.ConnectionActionCard
import app.gocast.ui.components.ConnectionActionSymbol
import app.gocast.ui.components.LastConnectedCard
import app.gocast.ui.components.PAIRING_CODE_LENGTH
import app.gocast.ui.components.PairingCodeInput
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
    startWithQualitySettings: Boolean = false,
    onShowWalkthrough: () -> Unit = {},
    viewModel: CastViewModel = viewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val trustedPc by viewModel.trustedPc.collectAsStateWithLifecycle()
    val discoveredHosts by viewModel.discoveredHosts.collectAsStateWithLifecycle()
    val joinBusy by viewModel.joinBusy.collectAsStateWithLifecycle()
    val castUiState by CaptureService.castUiState.collectAsStateWithLifecycle()
    val linkType by CastLinkMonitor.linkType.collectAsStateWithLifecycle()
    var mode by remember {
        mutableStateOf(if (startWithQualitySettings) ConnectMode.Quality else ConnectMode.Home)
    }
    var selectedHost by remember { mutableStateOf<DiscoveredHost?>(null) }
    var codeInput by remember { mutableStateOf("") }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onCaptureConsentResult(result.resultCode, result.data)
    }

    // Notifications + RECORD_AUDIO (needed for system playback capture), then
    // the MediaProjection sheet. Denial still continues — video-only cast works.
    val castPrepPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        captureLauncher.launch(CaptureConsent.createIntent(context))
    }

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
            setOverlayDrawableRes(R.drawable.ic_qr_code)
            setOverlayStringRes(R.string.scan_qr_hint)
            setHapticSuccessFeedback(true)
            setShowTorchToggle(true)
            setShowCloseButton(true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Only while resumed — Quick Cast owns consent when the app is in the background.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                when (event) {
                    CastEvent.RequestCaptureConsent -> {
                        val needed = buildList {
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            add(Manifest.permission.RECORD_AUDIO)
                        }.filter {
                            ContextCompat.checkSelfPermission(context, it) !=
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isEmpty()) {
                            captureLauncher.launch(CaptureConsent.createIntent(context))
                        } else {
                            castPrepPermissions.launch(needed.toTypedArray())
                        }
                    }
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

    LaunchedEffect(mode, trustedPc?.instanceId) {
        when (mode) {
            ConnectMode.Discover -> viewModel.startDiscovery()
            // Keep listening on Home so Last connected can light up when the PC appears.
            ConnectMode.Home -> {
                if (trustedPc != null) {
                    viewModel.startDiscovery()
                } else {
                    viewModel.stopDiscovery()
                }
            }
            ConnectMode.EnterCode -> Unit
            ConnectMode.Quality -> viewModel.stopDiscovery()
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
    // "Casting" / "Stop casting" only after MediaProjection is granted (or while paused).
    // WebSocket Connected alone is not active casting — consent may still be pending.
    val isActiveCast = castUiState == CaptureService.CastUiState.Casting ||
        castUiState == CaptureService.CastUiState.Paused

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            ConnectMode.Home -> {
                // A PC with a room is reachable through the internet, so it needs no mDNS sighting.
                val trustedOnline = trustedPc != null && (
                    trustedPc!!.roomId != null ||
                        discoveredHosts.any { it.instanceId == trustedPc!!.instanceId }
                    )
                HomeContent(
                    label = label,
                    connectionState = connectionState,
                    trustedPc = trustedPc,
                    trustedOnline = trustedOnline,
                    canJoin = canJoin,
                    isActiveCast = isActiveCast,
                    castPaused = castUiState == CaptureService.CastUiState.Paused,
                    linkType = linkType,
                    joinBusy = joinBusy,
                    onCastTrusted = { viewModel.castToTrustedPc() },
                    onTrustedOffline = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.last_connected_offline),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onScanQr = { qrScanner.launch(scannerConfig) },
                    onFindWifi = { mode = ConnectMode.Discover },
                    onResume = { viewModel.onResumeCastRequested() },
                    onStop = { viewModel.stopCast() },
                    onQuality = { mode = ConnectMode.Quality },
                    onShowWalkthrough = onShowWalkthrough,
                )
            }

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
                    codeInput = raw.uppercase().filter { ch -> ch.isLetterOrDigit() }
                        .take(PAIRING_CODE_LENGTH)
                },
                canJoin = canJoin,
                onConnect = {
                    val target = selectedHost ?: return@EnterCodeContent
                    if (codeInput.length < PAIRING_CODE_LENGTH) {
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
    trustedOnline: Boolean,
    canJoin: Boolean,
    isActiveCast: Boolean,
    castPaused: Boolean,
    linkType: LinkType?,
    joinBusy: Boolean,
    onCastTrusted: () -> Unit,
    onTrustedOffline: () -> Unit,
    onScanQr: () -> Unit,
    onFindWifi: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onQuality: () -> Unit,
    onShowWalkthrough: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = if (isActiveCast) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().verticalScroll(scrollState)
        },
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

        if (isActiveCast) {
            // Connected session: fill the screen with clear status + large actions
            // (M3 simple-screen pattern — avoid a tiny card floating in empty space).
            ActiveCastPanel(
                displayName = trustedPc?.displayName,
                paused = castPaused,
                linkType = linkType,
                onResume = onResume,
                onStop = onStop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.quality_live_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
            )
        } else {
            Spacer(Modifier.height(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = when (connectionState) {
                    is ConnectionState.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            if (connectionState is ConnectionState.Idle) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connect_choose_method),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        if (canJoin) {
            trustedPc?.let { pc ->
                LastConnectedCard(
                    pc = pc,
                    online = trustedOnline && canJoin,
                    onCastClick = onCastTrusted,
                    onOfflineClick = onTrustedOffline,
                    busy = joinBusy,
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
            ConnectionActionCard(
                title = stringResource(R.string.scan_qr),
                subtitle = stringResource(R.string.scan_qr_description),
                symbol = ConnectionActionSymbol.Qr,
                prominent = true,
                onClick = onScanQr,
            )
            Spacer(Modifier.height(12.dp))
            ConnectionActionCard(
                title = stringResource(R.string.find_on_wifi),
                subtitle = stringResource(R.string.find_wifi_description),
                symbol = ConnectionActionSymbol.Wifi,
                prominent = false,
                onClick = onFindWifi,
            )
            Spacer(Modifier.height(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onQuality) {
                Text(stringResource(R.string.quality_settings))
            }
            TextButton(onClick = onShowWalkthrough) {
                Text(stringResource(R.string.walkthrough_reopen))
            }
        }
    }
}

@Composable
private fun ActiveCastPanel(
    displayName: String?,
    paused: Boolean,
    linkType: LinkType?,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = displayName ?: stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (paused) {
                stringResource(R.string.cast_paused_title)
            } else {
                stringResource(R.string.cast_live_label)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (paused) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!paused && linkType != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    when (linkType) {
                        LinkType.Lan -> R.string.link_lan
                        LinkType.Internet -> R.string.link_internet
                        LinkType.Relayed -> R.string.link_relayed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (paused) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.cast_paused_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(36.dp))
        if (paused) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.resume_cast),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.stop_cast),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.stop_cast),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
        Spacer(Modifier.height(28.dp))
        PairingCodeInput(
            value = codeInput,
            onValueChange = onCodeChange,
            length = PAIRING_CODE_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConnect,
            enabled = canJoin && host != null && codeInput.length == PAIRING_CODE_LENGTH,
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
