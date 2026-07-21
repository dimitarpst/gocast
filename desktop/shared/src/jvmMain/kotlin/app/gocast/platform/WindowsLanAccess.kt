package app.gocast.platform

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Ensures the packaged Windows app can accept inbound LAN connections (TCP 8765).
 *
 * Gradle/`java` runs never reliably show the Windows Firewall Allow popup.
 * For the installed `gocast.exe` we:
 * 1. Try to add inbound Allow rules with `netsh` (not PowerShell) for the exe + port
 * 2. If that fails without elevation, the first listen on 8765 still triggers the
 *    normal Windows "Allow on private networks?" dialog for `gocast.exe`
 */
object WindowsLanAccess {

    private const val RULE_PROGRAM = "gocast"
    private const val RULE_PORT = "gocast TCP 8765"
    private const val PORT = 8765

    fun ensureInboundAccess(
        prefsDir: Path = Path.of(System.getProperty("user.home"), ".gocast"),
    ) {
        if (!isWindows()) return
        if (!isPackagedDistribution()) return
        if (alreadyConfigured(prefsDir)) return

        val exe = currentExePath()
        var ok = false
        if (exe != null) {
            ok = addProgramRule(exe) || ok
        }
        ok = addPortRule(PORT) || ok

        // Mark done if we installed a rule. If not, leave unmarked so a later
        // elevated/admin run can retry — the OS Allow dialog may still appear.
        if (ok) {
            markConfigured(prefsDir)
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    /** jpackage / Compose distributable sets this; Gradle `run` does not. */
    private fun isPackagedDistribution(): Boolean {
        if (!System.getProperty("compose.application.resources.dir").isNullOrBlank()) return true
        if (!System.getProperty("jpackage.app-path").isNullOrBlank()) return true
        val cmd = ProcessHandle.current().info().command().orElse("")
        return cmd.endsWith("gocast.exe", ignoreCase = true)
    }

    private fun currentExePath(): String? {
        val fromJpackage = System.getProperty("jpackage.app-path")
        if (!fromJpackage.isNullOrBlank()) return fromJpackage
        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        return cmd.takeIf { it.endsWith(".exe", ignoreCase = true) }
    }

    private fun addProgramRule(exePath: String): Boolean =
        runNetsh(
            "advfirewall", "firewall", "add", "rule",
            "name=$RULE_PROGRAM",
            "dir=in",
            "action=allow",
            "program=$exePath",
            "enable=yes",
            "profile=private",
        )

    private fun addPortRule(port: Int): Boolean =
        runNetsh(
            "advfirewall", "firewall", "add", "rule",
            "name=$RULE_PORT",
            "dir=in",
            "action=allow",
            "protocol=TCP",
            "localport=$port",
            "enable=yes",
            "profile=private",
        )

    private fun runNetsh(vararg args: String): Boolean {
        return try {
            val pb = ProcessBuilder("netsh", *args)
                .redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            code == 0 || output.contains("Ok.", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun flagFile(prefsDir: Path): Path = prefsDir.resolve("firewall-lan-ok")

    private fun alreadyConfigured(prefsDir: Path): Boolean = flagFile(prefsDir).exists()

    private fun markConfigured(prefsDir: Path) {
        prefsDir.createDirectories()
        flagFile(prefsDir).writeText("1")
    }
}
