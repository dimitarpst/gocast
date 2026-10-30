package app.gocast.discovery

import app.gocast.protocol.BeaconPayload
import java.io.File

/**
 * Broadcasts a small Bluetooth LE beacon (PC name + LAN address) so a phone
 * nearby can find this PC when mDNS is blocked. Windows only: the WinRT
 * publisher runs in a hidden Windows PowerShell child (`ble-beacon.ps1`),
 * which also exits on its own if this app dies.
 */
class BluetoothBeacon {
    private var process: Process? = null
    private var script: File? = null

    fun start(host: String, port: Int, displayName: String) {
        stop()
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val payload = BeaconPayload.encode(host, port, displayName) ?: return
        val file = script ?: extractScript()?.also { script = it } ?: return
        process = runCatching {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath,
                "-Payload", payload.joinToString(",") { (it.toInt() and 0xFF).toString() },
                "-ParentPid", ProcessHandle.current().pid().toString(),
                "-CompanyId", BeaconPayload.COMPANY_ID.toString(),
            )
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

    private fun extractScript(): File? = runCatching {
        val source = BluetoothBeacon::class.java.getResourceAsStream("/ble-beacon.ps1") ?: return null
        File.createTempFile("gocast-ble-", ".ps1").apply {
            deleteOnExit()
            source.use { input -> outputStream().use { input.copyTo(it) } }
        }
    }.getOrNull()
}
