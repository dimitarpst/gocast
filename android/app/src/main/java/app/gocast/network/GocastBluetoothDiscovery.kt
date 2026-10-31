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
import android.util.Log
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
 * Finds GoCast PCs nearby from their Bluetooth LE beacons, even when they are
 * on another network. The room beacons carry the PC's relay room, so joining
 * goes through the relay with the usual pairing code, LAN as fallback.
 */
class GocastBluetoothDiscovery(context: Context) {
    private val appContext = context.applicationContext

    fun isSupported(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun isEnabled(): Boolean = adapter()?.isEnabled == true

    // Callers request BLUETOOTH_SCAN (Android 12+) or location (10–11) first.
    @SuppressLint("MissingPermission")
    fun discover(): Flow<List<DiscoveredHost>> = callbackFlow {
        val adapter = adapter()?.takeIf { it.isEnabled }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val lastSeen = ConcurrentHashMap<String, Pair<DiscoveredHost, Long>>()
        val heard = ConcurrentHashMap.newKeySet<String>()

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
                // One PC sends up to three beacons with the same IPv4: name (v1),
                // room (v3, no name or port) and both (v2, Bluetooth 5 only).
                // Merge them: the room from any, the fullest name, a known port.
                val key = "bt:${beacon.host}"
                val known = lastSeen[key]?.first
                val kind = when {
                    beacon.port == 0 -> "room"
                    beacon.roomId != null -> "room+name (Bluetooth 5)"
                    else -> "name"
                }
                if (heard.add("$key $kind")) Log.i(TAG, "Heard $kind beacon from ${beacon.host}")
                lastSeen[key] = DiscoveredHost(
                    displayName = mergedName(beacon.displayName, known?.displayName),
                    host = beacon.host,
                    port = beacon.port.takeIf { it != 0 } ?: known?.port ?: DEFAULT_PC_PORT,
                    instanceId = key,
                    roomId = beacon.roomId ?: known?.roomId,
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
            .apply {
                // Also hear the Bluetooth 5 beacon that carries the room, where the chip can.
                if (adapter?.isLeExtendedAdvertisingSupported == true) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }
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

    /**
     * The room beacon has no name; the name beacon cuts it to 15 bytes, which the
     * Bluetooth 5 beacon may already have sent in full.
     */
    private fun mergedName(heard: String, known: String?): String = when {
        heard.isBlank() -> known ?: "gocast PC"
        known != null && known.length > heard.length && known.startsWith(heard) -> known
        else -> heard
    }

    private fun adapter() =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private companion object {
        const val TAG = "BluetoothDiscovery"
        const val STALE_AFTER_MS = 10_000L
        /** The PC's signaling port, for a PC heard only through its room beacon. */
        const val DEFAULT_PC_PORT = 8765
    }
}
