package app.gocast.platform

import kotlin.system.exitProcess

/**
 * Relaunches the packaged Windows exe and kills this process.
 * Dev (`gradle run` / raw `java`) cannot reliably rebuild the command line on Windows.
 */
object AppRelaunch {

    fun canRelaunchPackagedApp(): Boolean = packagedExePath() != null

    fun relaunchPackagedAppOrExit() {
        val exe = packagedExePath()
        if (exe != null) {
            ProcessBuilder(exe).start()
        }
        // Hard exit — do not wait for KCEF.disposeBlocking (that is the slow X).
        exitProcess(0)
    }

    private fun packagedExePath(): String? {
        val fromJpackage = System.getProperty("jpackage.app-path")
        if (!fromJpackage.isNullOrBlank()) return fromJpackage

        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        return cmd.takeIf { it.endsWith("gocast.exe", ignoreCase = true) }
    }
}
