package app.gocast.platform

import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Opens inbound LAN access for packaged Windows builds (TCP 8765).
 *
 * Silent non-elevated `netsh` does nothing. The Windows "Allow app?" dialog also
 * does not reliably appear for jpackage/`gocast.exe`. So we request elevation
 * (UAC) and add firewall Allow rules via `netsh` — never PowerShell.
 *
 * Fresh MSI installs always show the LAN setup screen once, even if a leftover
 * firewall rule already exists from a previous install.
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

    fun isPackagedWindows(): Boolean =
        isWindows() && isPackagedDistribution()

    /** Show the LAN setup UI before QR on packaged Windows. */
    fun shouldShowLanSetup(): Boolean {
        if (!isPackagedWindows()) return false
        if (!firstRunCompleted()) return true
        return statusRef.get() == Status.Blocked
    }

    fun markFirstRunCompleted(prefsDir: Path = GocastDataDirs.root().toPath()) {
        prefsDir.createDirectories()
        firstRunFile(prefsDir).writeText("1")
    }

    /** Cheap check at process start — no UAC. */
    fun refreshStatus() {
        if (!isPackagedWindows()) {
            statusRef.set(Status.NotApplicable)
            return
        }
        GocastDataDirs.migrateLegacyHomePrefs()
        val prefs = GocastDataDirs.root().toPath()
        if (rulesPresent()) {
            markFirewallOk(prefs)
            statusRef.set(Status.Allowed)
        } else {
            statusRef.set(Status.Blocked)
        }
    }

    /**
     * Shows UAC and adds Allow rules when needed. If rules already exist, returns
     * [Status.Allowed] without prompting.
     */
    fun requestAllowNow(
        prefsDir: Path = GocastDataDirs.root().toPath(),
    ): Status {
        if (!isPackagedWindows()) {
            statusRef.set(Status.NotApplicable)
            return status()
        }
        if (rulesPresent()) {
            markFirewallOk(prefsDir)
            statusRef.set(Status.Allowed)
            return status()
        }
        val ok = addRulesElevated(currentExePath())
        if (ok || rulesPresent()) {
            markFirewallOk(prefsDir)
            statusRef.set(Status.Allowed)
        } else {
            runCatching { firewallFlagFile(prefsDir).toFile().delete() }
            statusRef.set(Status.Blocked)
        }
        return status()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    private fun isPackagedDistribution(): Boolean {
        // jpackage / installed MSI only. Do NOT treat compose.application.resources.dir
        // alone — Gradle `run` sets that and would falsely skip / fake firewall state.
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

    /**
     * Port rule is required. Program-only Allow is not enough after reinstall /
     * path changes, and does nothing for non-exe hosts.
     */
    private fun rulesPresent(): Boolean {
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

    private fun firewallFlagFile(prefsDir: Path): Path = prefsDir.resolve("firewall-lan-ok")

    private fun firstRunFile(prefsDir: Path): Path = prefsDir.resolve("lan-setup-completed")

    private fun firstRunCompleted(prefsDir: Path = GocastDataDirs.root().toPath()): Boolean =
        firstRunFile(prefsDir).exists()

    private fun markFirewallOk(prefsDir: Path) {
        prefsDir.createDirectories()
        firewallFlagFile(prefsDir).writeText("1")
    }
}
