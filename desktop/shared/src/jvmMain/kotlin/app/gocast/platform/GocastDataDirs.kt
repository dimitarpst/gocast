package app.gocast.platform

import java.io.File

/**
 * Writable per-user dirs. Relative paths like `kcef-bundle` break for MSI installs
 * (cwd under Program Files, or a different cwd each launch).
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
}
