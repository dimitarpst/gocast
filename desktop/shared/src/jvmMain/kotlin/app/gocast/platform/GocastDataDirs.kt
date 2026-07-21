package app.gocast.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writable per-user dirs. Relative paths like `kcef-bundle` break for MSI installs
 * (cwd under Program Files, or a different cwd each launch).
 *
 * Everything lives under `%LOCALAPPDATA%\gocast` so uninstall can wipe one tree.
 */
object GocastDataDirs {
    fun root(): File {
        val localAppData = System.getenv("LOCALAPPDATA")
        val base = if (!localAppData.isNullOrBlank()) {
            File(localAppData, "gocast")
        } else {
            File(System.getProperty("user.home"), ".gocast")
        }
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }

    fun kcefBundle(): File = File(root(), "kcef-bundle").also { it.mkdirs() }

    fun kcefCache(): File = File(root(), "kcef-cache").also { it.mkdirs() }

    /**
     * Older builds stored identity under `~/.gocast`. Copy into LocalAppData once
     * so upgrades keep the same PC name / trust tokens.
     */
    fun migrateLegacyHomePrefs() {
        val legacy = File(System.getProperty("user.home"), ".gocast")
        if (!legacy.isDirectory) return
        val dest = root()
        if (legacy.canonicalPath == dest.canonicalPath) return
        val names = listOf(
            "identity.json",
            "trusted-devices.json",
            "firewall-lan-ok",
            "lan-setup-completed",
        )
        for (name in names) {
            val from = File(legacy, name)
            val to = File(dest, name)
            if (from.isFile && !to.exists()) {
                runCatching {
                    Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }
}
