package app.gocast.platform

import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Opens inbound LAN access for packaged Windows builds (TCP 8765).
 *
 * Silent non-elevated `netsh` does nothing. The Windows "Allow app?" dialog also
 * does not reliably appear for jpackage/`gocast.exe`. So we request elevation
 * (UAC) and add firewall Allow rules via `netsh` — never PowerShell.
 */
object WindowsLanAccess {

    private const val RULE_PROGRAM = "gocast"
    private const val RULE_PORT = "gocast TCP 8765"
    private const val PORT = 8765

    enum class Status {
        /** Not Windows, or Gradle/dev run — leave firewall alone. */
        NotApplicable,
        /** Rules are present (or we successfully added them). */
        Allowed,
        /** Need UAC / user action — phone will time out until this is fixed. */
        Blocked,
    }

    private val statusRef = AtomicReference(Status.NotApplicable)

    fun status(): Status = statusRef.get()

    /** Cheap check at process start — no UAC. */
    fun refreshStatus() {
        if (!isWindows() || !isPackagedDistribution()) {
            statusRef.set(Status.NotApplicable)
            return
        }
        if (rulesPresent()) {
            markConfigured(Path.of(System.getProperty("user.home"), ".gocast"))
            statusRef.set(Status.Allowed)
        } else {
            statusRef.set(Status.Blocked)
        }
    }

    /**
     * Shows UAC and adds Allow rules. Call from UI when [status] is [Status.Blocked].
     */
    fun requestAllowNow(
        prefsDir: Path = Path.of(System.getProperty("user.home"), ".gocast"),
    ): Status {
        if (!isWindows() || !isPackagedDistribution()) {
            statusRef.set(Status.NotApplicable)
            return status()
        }
        if (rulesPresent()) {
            markConfigured(prefsDir)
            statusRef.set(Status.Allowed)
            return status()
        }
        val ok = addRulesElevated(currentExePath())
        if (ok || rulesPresent()) {
            markConfigured(prefsDir)
            statusRef.set(Status.Allowed)
        } else {
            runCatching { flagFile(prefsDir).toFile().delete() }
            statusRef.set(Status.Blocked)
        }
        return status()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

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
        return cmd.takeIf { it.endsWith("gocast.exe", ignoreCase = true) }
    }

    private fun rulesPresent(): Boolean {
        val program = runNetshCapture(
            "advfirewall", "firewall", "show", "rule", "name=$RULE_PROGRAM",
        )
        if (program.ok &&
            program.output.contains(RULE_PROGRAM, ignoreCase = true) &&
            !program.output.contains("No rules match", ignoreCase = true)
        ) {
            return true
        }
        val port = runNetshCapture(
            "advfirewall", "firewall", "show", "rule", "name=$RULE_PORT",
        )
        return port.ok &&
            port.output.contains("8765") &&
            !port.output.contains("No rules match", ignoreCase = true)
    }

    private fun addRulesElevated(exePath: String?): Boolean {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gocast-fw").also { it.mkdirs() }
        val pid = ProcessHandle.current().pid()
        val done = File(tempDir, "done-$pid.txt")
        val script = File(tempDir, "allow-$pid.cmd")
        val vbs = File(tempDir, "elevate-$pid.vbs")
        done.delete()

        val lines = mutableListOf("@echo off")
        if (exePath != null) {
            lines += "netsh advfirewall firewall delete rule name=\"$RULE_PROGRAM\" >nul 2>&1"
            lines += "netsh advfirewall firewall add rule name=\"$RULE_PROGRAM\" " +
                "dir=in action=allow program=\"$exePath\" enable=yes profile=any"
        }
        lines += "netsh advfirewall firewall delete rule name=\"$RULE_PORT\" >nul 2>&1"
        lines += "netsh advfirewall firewall add rule name=\"$RULE_PORT\" " +
            "dir=in action=allow protocol=TCP localport=$PORT enable=yes profile=any"
        lines += "echo ok>\"${done.absolutePath}\""
        script.writeText(lines.joinToString("\r\n"), Charsets.UTF_8)

        val cmdPath = script.absolutePath
        vbs.writeText(
            "Set sh = CreateObject(\"Shell.Application\")\r\n" +
                // 0 = hidden window — no scary black CMD flash for the user.
                "sh.ShellExecute \"cmd.exe\", \"/c \"\"$cmdPath\"\"\", \"\", \"runas\", 0\r\n",
            Charsets.UTF_8,
        )

        return try {
            ProcessBuilder("wscript.exe", "//nologo", vbs.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()

            // User may take a few seconds on UAC; cancel → no done file.
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline) {
                if (done.exists()) {
                    return rulesPresent()
                }
                // If rules appeared from another path, succeed early.
                if (rulesPresent()) return true
                Thread.sleep(400)
            }
            rulesPresent()
        } catch (_: Exception) {
            false
        } finally {
            runCatching { done.delete() }
            runCatching { script.delete() }
            runCatching { vbs.delete() }
        }
    }

    private data class NetshResult(val ok: Boolean, val output: String)

    private fun runNetshCapture(vararg args: String): NetshResult {
        return try {
            val process = ProcessBuilder("netsh", *args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            NetshResult(code == 0, output)
        } catch (_: Exception) {
            NetshResult(false, "")
        }
    }

    private fun flagFile(prefsDir: Path): Path = prefsDir.resolve("firewall-lan-ok")

    private fun markConfigured(prefsDir: Path) {
        prefsDir.createDirectories()
        flagFile(prefsDir).writeText("1")
    }
}
