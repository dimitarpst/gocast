package app.gocast.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import app.gocast.ui.components.ConnectionActionCard
import app.gocast.ui.components.ConnectionActionSymbol
import app.gocast.ui.components.LastConnectedCard
import app.gocast.ui.components.PAIRING_CODE_LENGTH
import app.gocast.ui.components.PairingCodeInput
import app.gocast.ui.components.UpdateCard
import app.gocast.ui.quality.QualitySettingsScreen
import app.gocast.update.PhoneUpdateState
import app.gocast.update.UpdateViewModel
import kotlinx.coroutines.awaitCancellation

private enum class ConnectMode {
    Home,
    Scan,
    Discover,
    BluetoothDiscover,
    EnterCode,
    Quality,
}

/** Android 12+ asks for "Nearby devices"; 10–11 can only scan with location. */
private val bluetoothScanPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun ConnectScreen(
    deepLinkUri: Uri?,
    resumeCastRequestId: Int = 0,
    startWithQualitySettings: Boolean = false,
    onShowWalkthrough: () -> Unit = {},
    viewModel: CastViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val trustedPc by viewModel.trustedPc.collectAsStateWithLifecycle()
    val discoveredHosts by viewModel.discoveredHosts.collectAsStateWithLifecycle()
    val bluetoothHosts by viewModel.bluetoothHosts.collectAsStateWithLifecycle()
    val joinBusy by viewModel.joinBusy.collectAsStateWithLifecycle()
    val castUiState by CaptureService.castUiState.collectAsStateWithLifecycle()
    var mode by remember {
        mutableStateOf(if (startWithQualitySettings) ConnectMode.Quality else ConnectMode.Home)
    }
    var selectedHost by remember { mutableStateOf<DiscoveredHost?>(null) }
    // Which list Enter code returns to.
    var hostListMode by remember { mutableStateOf(ConnectMode.Discover) }
    var bluetoothOn by remember { mutableStateOf(true) }
    var codeInput by remember { mutableStateOf("") }

    val bluetoothDeniedMessage = stringResource(R.string.bluetooth_permission_denied)
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            mode = ConnectMode.BluetoothDiscover
        } else {
            Toast.makeText(context, bluetoothDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current

    // Back from Android's "Install unknown apps" setting: finish a waiting update.
    LaunchedEffect(updateViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            updateViewModel.onResumed()
        }
    }

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
            if (mode == ConnectMode.Discover ||
                mode == ConnectMode.BluetoothDiscover ||
                mode == ConnectMode.EnterCode
            ) {
                mode = ConnectMode.Home
                viewModel.stopDiscovery()
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(mode) {
        when (mode) {
            // Keep listening on Home so Last connected can light up and
            // Find on Wi-Fi can show how many PCs are already there.
            ConnectMode.Home,
            ConnectMode.Discover,
            -> viewModel.startDiscovery()
            // Scanning leaves Home's discovery as it was, like the old scanner activity did.
            ConnectMode.Scan,
            ConnectMode.BluetoothDiscover,
            ConnectMode.EnterCode,
            -> Unit
            ConnectMode.Quality -> viewModel.stopDiscovery()
        }
    }

    // Scan only while the Bluetooth list is on screen; re-check on return
    // from Settings in case the user just switched Bluetooth on.
    LaunchedEffect(mode, lifecycleOwner) {
        if (mode != ConnectMode.BluetoothDiscover) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            bluetoothOn = viewModel.isBluetoothOn()
            if (!bluetoothOn) return@repeatOnLifecycle
            viewModel.startBluetoothDiscovery()
            try {
                awaitCancellation()
            } finally {
                viewModel.stopBluetoothDiscovery()
            }
        }
    }

    BackHandler(enabled = mode != ConnectMode.Home) {
        when (mode) {
            ConnectMode.Quality,
            ConnectMode.Scan,
            ConnectMode.BluetoothDiscover,
            -> mode = ConnectMode.Home
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

    if (mode == ConnectMode.Scan) {
        QrScanScreen(
            onScanned = { uri ->
                mode = ConnectMode.Home
                viewModel.onDeepLink(uri)
            },
            onBack = { mode = ConnectMode.Home },
            onFindWifi = { mode = ConnectMode.Discover },
        )
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

    if (mode == ConnectMode.Discover) {
        FindOnWifiScreen(
            hosts = discoveredHosts,
            onHostClick = { host ->
                selectedHost = host
                codeInput = ""
                hostListMode = ConnectMode.Discover
                mode = ConnectMode.EnterCode
            },
            onScanQr = { mode = ConnectMode.Scan },
            onBack = {
                viewModel.stopDiscovery()
                mode = ConnectMode.Home
            },
        )
        return
    }

    if (mode == ConnectMode.BluetoothDiscover) {
        FindWithBluetoothScreen(
            hosts = bluetoothHosts,
            bluetoothOn = bluetoothOn,
            onTurnOnBluetooth = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            onHostClick = { host ->
                selectedHost = host
                codeInput = ""
                hostListMode = ConnectMode.BluetoothDiscover
                mode = ConnectMode.EnterCode
            },
            onScanQr = { mode = ConnectMode.Scan },
            onBack = { mode = ConnectMode.Home },
        )
        return
    }

    if (mode == ConnectMode.EnterCode) {
        EnterCodeScreen(
            host = selectedHost,
            codeInput = codeInput,
            onCodeChange = { raw ->
                codeInput = raw.uppercase().filter { ch -> ch.isLetterOrDigit() }
                    .take(PAIRING_CODE_LENGTH)
            },
            canJoin = canJoin,
            connecting = connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting,
            error = (connectionState as? ConnectionState.Failed)?.reason,
            onConnect = {
                val target = selectedHost ?: return@EnterCodeScreen
                if (codeInput.length < PAIRING_CODE_LENGTH) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.code_too_short),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@EnterCodeScreen
                }
                viewModel.stopDiscovery()
                viewModel.startJoin(
                    JoinParams(
                        host = target.host,
                        port = target.port,
                        token = codeInput,
                        // From the Bluetooth beacon: join through the relay, LAN as fallback.
                        roomId = target.roomId,
                    ),
                )
            },
            onBack = {
                codeInput = ""
                mode = hostListMode
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Status bar, 3-button / gesture nav bar, cutouts and keyboard.
            .safeDrawingPadding()
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
                    joinBusy = joinBusy,
                    onCastTrusted = { viewModel.castToTrustedPc() },
                    onTrustedOffline = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.last_connected_offline),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onScanQr = { mode = ConnectMode.Scan },
                    wifiHostCount = discoveredHosts.size,
                    onFindWifi = { mode = ConnectMode.Discover },
                    onFindBluetooth = if (viewModel.bluetoothSupported) {
                        {
                            val missing = bluetoothScanPermissions.filter {
                                ContextCompat.checkSelfPermission(context, it) !=
                                    PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                mode = ConnectMode.BluetoothDiscover
                            } else {
                                bluetoothPermissionLauncher.launch(missing.toTypedArray())
                            }
                        }
                    } else {
                        null
                    },
                    onResume = { viewModel.onResumeCastRequested() },
                    onStop = { viewModel.stopCast() },
                    onRefresh = { viewModel.refreshCast() },
                    onQuality = { mode = ConnectMode.Quality },
                    onShowWalkthrough = onShowWalkthrough,
                    updateState = updateState,
                    onUpdate = { updateViewModel.startUpdate() },
                    onAllowInstalls = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }

            ConnectMode.Discover,
            ConnectMode.BluetoothDiscover,
            ConnectMode.EnterCode,
            ConnectMode.Quality,
            ConnectMode.Scan,
            -> Unit
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
    joinBusy: Boolean,
    onCastTrusted: () -> Unit,
    onTrustedOffline: () -> Unit,
    onScanQr: () -> Unit,
    wifiHostCount: Int,
    onFindWifi: () -> Unit,
    /** Null when the phone has no Bluetooth LE. */
    onFindBluetooth: (() -> Unit)?,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onQuality: () -> Unit,
    onShowWalkthrough: () -> Unit,
    updateState: PhoneUpdateState,
    onUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    val scrollState = rememberScrollState()
    // Connecting, or retrying after a failure (the connector keeps trying until stopped).
    val trying = connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.Reconnecting ||
        connectionState is ConnectionState.Failed
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
                reconnecting = connectionState !is ConnectionState.Connected,
                onResume = onResume,
                onStop = onStop,
                onRefresh = onRefresh,
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
            // A failed attempt is retried on its own, so show that calmly; the
            // reason is a small hint, not a red headline.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trying) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = if (connectionState is ConnectionState.Failed) {
                        stringResource(R.string.reaching_pc)
                    } else {
                        label
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            if (connectionState is ConnectionState.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = connectionState.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
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

        // Updates only while nothing is under way: installing restarts the app.
        if (connectionState is ConnectionState.Idle && !isActiveCast &&
            updateState != PhoneUpdateState.None
        ) {
            UpdateCard(state = updateState, onUpdate = onUpdate, onAllowInstalls = onAllowInstalls)
            Spacer(Modifier.height(20.dp))
        }

        if (trying && !isActiveCast) {
            // While it connects, the only useful action is to stop trying.
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel_connecting),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Other ways to connect only when nothing is under way; mid-cast they
        // squeezed the casting panel (30 Oct screenshot).
        if (connectionState is ConnectionState.Idle && !isActiveCast) {
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
                badgeCount = wifiHostCount,
            )
            if (onFindBluetooth != null) {
                Spacer(Modifier.height(12.dp))
                ConnectionActionCard(
                    title = stringResource(R.string.find_with_bluetooth),
                    subtitle = stringResource(R.string.find_bluetooth_description),
                    symbol = ConnectionActionSymbol.Bluetooth,
                    prominent = false,
                    onClick = onFindBluetooth,
                )
            }
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
    /** The link to the PC dropped (e.g. Wi-Fi → mobile data); the cast resumes on its own. */
    reconnecting: Boolean,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (reconnecting && !paused) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = when {
                    paused -> stringResource(R.string.cast_paused_title)
                    reconnecting -> stringResource(R.string.reconnecting)
                    else -> stringResource(R.string.cast_live_label)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (paused || reconnecting) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
            )
        }
        // The link type (Direct / Relayed) is only logged by WebRtcPublisher, not shown.
        val codecFallback by CastLinkMonitor.codecFallback.collectAsStateWithLifecycle()
        val fallback = codecFallback
        if (!paused && fallback != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    fallback.messageRes,
                    CastLinkMonitor.codecLabel(fallback.wanted),
                    CastLinkMonitor.codecLabel(fallback.used),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
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
            Spacer(Modifier.height(8.dp))
            // For when the PC's picture looks stuck: reconnect and send fresh video.
            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.refresh_cast))
            }
        }
    }
}

