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
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.Image
import app.gocast.R
import app.gocast.domain.DiscoveredHost
import app.gocast.ui.components.PAIRING_CODE_LENGTH
import app.gocast.ui.components.PairingCodeInput
import app.gocast.ui.components.ScreenTopBar
import app.gocast.ui.components.SettingsGroup
import app.gocast.ui.components.drawBluetoothSymbol
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
    FindPcsScreen(
        title = stringResource(R.string.find_on_wifi),
        looking = stringResource(R.string.looking_for_pcs),
        nothingYet = stringResource(R.string.no_pcs_yet),
        heading = stringResource(R.string.wifi_pcs_heading),
        symbol = { SearchingWifi() },
        hosts = hosts,
        onHostClick = onHostClick,
        onScanQr = onScanQr,
        onBack = onBack,
    )
}

/** PCs whose Bluetooth beacon this phone hears, whatever network they are on. */
@Composable
fun FindWithBluetoothScreen(
    hosts: List<DiscoveredHost>,
    bluetoothOn: Boolean,
    onTurnOnBluetooth: () -> Unit,
    onHostClick: (DiscoveredHost) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    FindPcsScreen(
        title = stringResource(R.string.find_with_bluetooth),
        looking = stringResource(R.string.looking_for_pcs_bluetooth),
        nothingYet = stringResource(R.string.no_pcs_bluetooth),
        heading = stringResource(R.string.bluetooth_pcs_heading),
        symbol = { SearchingBluetooth() },
        hosts = hosts,
        onHostClick = onHostClick,
        onScanQr = onScanQr,
        onBack = onBack,
        problem = if (bluetoothOn) null else stringResource(R.string.bluetooth_off),
        fixLabel = stringResource(R.string.bluetooth_turn_on),
        onFix = onTurnOnBluetooth,
    )
}

/** Shared layout for the PC lists. A [problem] replaces the search with its [onFix] button. */
@Composable
private fun FindPcsScreen(
    title: String,
    looking: String,
    nothingYet: String,
    heading: String,
    symbol: @Composable () -> Unit,
    hosts: List<DiscoveredHost>,
    onHostClick: (DiscoveredHost) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    problem: String? = null,
    fixLabel: String = "",
    onFix: () -> Unit = {},
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
        ScreenTopBar(title = title, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            symbol()
            if (problem != null) {
                Text(
                    problem,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                )
                Button(
                    onClick = onFix,
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp),
                ) {
                    Text(fixLabel)
                }
            } else if (hosts.isEmpty()) {
                TextWithLogo(
                    if (waitedLong) nothingYet else looking,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                )
            } else {
                SettingsGroup(title = heading) {
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

/** Centred text where the word "gocast" is drawn as the logo, sized to the text. */
@Composable
private fun TextWithLogo(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val at = text.indexOf(LOGO_WORD)
    if (at < 0) {
        Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier)
        return
    }
    val annotated = buildAnnotatedString {
        append(text.substring(0, at))
        appendInlineContent(LOGO_WORD, LOGO_WORD)
        append(text.substring(at + LOGO_WORD.length))
    }
    val logo = mapOf(
        LOGO_WORD to InlineTextContent(
            // The logo is about 3.6 times as wide as it is tall.
            Placeholder(width = 3.4.em, height = 0.95.em, PlaceholderVerticalAlign.TextCenter),
        ) {
            Image(
                painter = painterResource(R.drawable.gocast_logo),
                contentDescription = LOGO_WORD,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
    Text(
        annotated,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        inlineContent = logo,
        modifier = modifier,
    )
}

private const val LOGO_WORD = "gocast"

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

/** The Bluetooth rune breathing in and out while the phone listens for beacons. */
@Composable
private fun SearchingBluetooth() {
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
            val glow = sin(phase * PI).toFloat()
            drawBluetoothSymbol(color.copy(alpha = 0.35f + 0.65f * glow))
        }
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
