package app.gocast.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gocast.R
import app.gocast.domain.DiscoveredHost
import app.gocast.ui.components.PAIRING_CODE_LENGTH
import app.gocast.ui.components.PairingCodeInput
import app.gocast.ui.components.ScreenTopBar
import app.gocast.ui.components.SettingsGroup
import app.gocast.ui.components.drawWifiSymbol
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** PCs found on this Wi‑Fi, in the same grouped style as the Quality screen. */
@Composable
fun FindOnWifiScreen(
    hosts: List<DiscoveredHost>,
    onHostClick: (DiscoveredHost) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    // After a while with nothing found, point at the likely cause instead of "looking…" forever.
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(8_000)
        waitedLong = true
    }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        ScreenTopBar(title = stringResource(R.string.find_on_wifi), onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            SearchingWifi()
            if (hosts.isEmpty()) {
                Text(
                    stringResource(if (waitedLong) R.string.no_pcs_yet else R.string.looking_for_pcs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                )
            } else {
                SettingsGroup(title = stringResource(R.string.wifi_pcs_heading)) {
                    hosts.forEach { host -> HostRow(host, onClick = { onHostClick(host) }) }
                }
            }
        }
        FilledTonalButton(
            onClick = onScanQr,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.scan_qr))
        }
    }
}

/** The pairing code for one PC. Shows progress and the failure reason on this screen. */
@Composable
fun EnterCodeScreen(
    host: DiscoveredHost?,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    canJoin: Boolean,
    connecting: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    // Only show a failure that came from this screen's own attempt, until the code is edited.
    var attempted by remember { mutableStateOf(false) }
    val showError = attempted && error != null && !connecting
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        ScreenTopBar(title = stringResource(R.string.enter_code), onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            if (host != null) {
                PcAvatar(host.displayName, 64.dp)
                Text(
                    host.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            PairingCodeInput(
                value = codeInput,
                onValueChange = {
                    attempted = false
                    onCodeChange(it)
                },
                length = PAIRING_CODE_LENGTH,
                enabled = !connecting,
            )
            Text(
                if (showError) error.orEmpty() else stringResource(R.string.enter_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (showError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            )
        }
        Button(
            onClick = {
                attempted = true
                onConnect()
            },
            enabled = canJoin && host != null && codeInput.length == PAIRING_CODE_LENGTH,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.connecting))
            } else {
                Text(stringResource(R.string.connect))
            }
        }
    }
}

@Composable
private fun HostRow(host: DiscoveredHost, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcAvatar(host.displayName, 40.dp)
        Text(
            host.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The PC's first letter in a circle, like a contact avatar. */
@Composable
private fun PcAvatar(name: String, size: Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** The approved Wi‑Fi symbol with its bands lighting up from the dot outwards, on a loop. */
@Composable
private fun SearchingWifi() {
    val transition = rememberInfiniteTransition(label = "searching")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(112.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(56.dp)) {
            drawWifiSymbol(color) { band ->
                // Each band glows for half a cycle, the outer ones a little later.
                val local = ((phase - band * 0.18f) % 1f + 1f) % 1f
                val glow = if (local < 0.5f) sin(local / 0.5f * PI).toFloat() else 0f
                0.25f + 0.75f * glow
            }
        }
    }
}
