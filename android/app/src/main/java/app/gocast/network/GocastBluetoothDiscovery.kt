package app.gocast.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import app.gocast.domain.DiscoveredHost
import app.gocast.protocol.BeaconPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds GoCast PCs nearby from their Bluetooth LE beacon, even when they are
 * on another network. The beacon carries the PC's LAN address, so joining it
 * still uses the normal pairing-code flow.
 */
class GocastBluetoothDiscovery(context: Context) {
    private val appContext = context.applicationContext

    fun isSupported(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun isEnabled(): Boolean = adapter()?.isEnabled == true

    // Callers request BLUETOOTH_SCAN (Android 12+) or location (10–11) first.
    @SuppressLint("MissingPermission")
    fun discover(): Flow<List<DiscoveredHost>> = callbackFlow {
        val scanner = adapter()?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val lastSeen = ConcurrentHashMap<String, Pair<DiscoveredHost, Long>>()

        fun emitHosts() {
            val cutoff = SystemClock.elapsedRealtime() - STALE_AFTER_MS
            lastSeen.entries.removeIf { it.value.second < cutoff }
            trySend(lastSeen.values.map { it.first }.sortedBy { it.displayName.lowercase() })
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val data = result.scanRecord
                    ?.getManufacturerSpecificData(BeaconPayload.COMPANY_ID) ?: return
                val beacon = BeaconPayload.decode(data) ?: return
                val key = "bt:${beacon.host}:${beacon.port}"
                lastSeen[key] = DiscoveredHost(
                    displayName = beacon.displayName,
                    host = beacon.host,
                    port = beacon.port,
                    instanceId = key,
                ) to SystemClock.elapsedRealtime()
                emitHosts()
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }

        // Prefix match on "GC" keeps other test beacons out and lets the
        // chip filter in hardware.
        val filter = ScanFilter.Builder()
            .setManufacturerData(BeaconPayload.COMPANY_ID, BeaconPayload.MAGIC)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        emitHosts()

        // Drop PCs whose beacon stopped (app closed, walked away).
        launch {
            while (isActive) {
                delay(STALE_AFTER_MS / 2)
                emitHosts()
            }
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun adapter() =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private companion object {
        const val STALE_AFTER_MS = 10_000L
    }
}
