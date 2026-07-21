package app.gocast.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writable per-user dirs under `%LOCALAPPDATA%\gocast` so uninstall can wipe one tree.
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
