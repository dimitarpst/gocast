package app.gocast.discovery

import app.gocast.protocol.BeaconPayload
import java.io.File

/**
 * Broadcasts this PC's Bluetooth LE beacons so a phone nearby can find it on
 * any network: a short one (name + LAN address) every phone hears, and a
 * Bluetooth 5 one that adds the relay room. Windows only: the WinRT
 * publishers run in a hidden Windows PowerShell child (`ble-beacon.ps1`),
 * which also exits on its own if this app dies.
 */
class BluetoothBeacon {
    private var process: Process? = null
    private var script: File? = null

    fun start(host: String, port: Int, displayName: String, roomId: String?) {
        stop()
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val payload = BeaconPayload.encode(host, port, displayName) ?: return
        val roomPayload = roomId?.let { BeaconPayload.encodeWithRoom(host, port, displayName, it) }
        val file = script ?: extractScript()?.also { script = it } ?: return
        val args = mutableListOf(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-File", file.absolutePath,
            "-Payload", payload.toCsv(),
            "-ParentPid", ProcessHandle.current().pid().toString(),
            "-CompanyId", BeaconPayload.COMPANY_ID.toString(),
        )
        if (roomPayload != null) args += listOf("-RoomPayload", roomPayload.toCsv())
        process = runCatching {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull()
    }

    fun stop() {
        process?.let { running ->
            running.destroy()
            if (!running.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) running.destroyForcibly()
        }
        process = null
    }

    private fun ByteArray.toCsv(): String = joinToString(",") { (it.toInt() and 0xFF).toString() }

    private fun extractScript(): File? = runCatching {
        val source = BluetoothBeacon::class.java.getResourceAsStream("/ble-beacon.ps1") ?: return null
        File.createTempFile("gocast-ble-", ".ps1").apply {
            deleteOnExit()
            source.use { input -> outputStream().use { input.copyTo(it) } }
        }
    }.getOrNull()
}
