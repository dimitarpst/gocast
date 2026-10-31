package app.gocast.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.gocast.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A newer GoCast APK on GitHub that passed the same checks as the PC's updater. */
data class PhoneUpdate(val version: String, val url: String, val size: Long, val sha256: String)

/**
 * One-tap updates for the sideloaded phone app, like the PC's (ReleaseCatalog /
 * GitHubUpdates): read the latest stable GitHub release, download exactly
 * `gocast-<version>.apk`, check its size and GitHub's SHA-256, then hand it to
 * Android's installer, which always asks the user to confirm. Updating in place
 * works only because every release is signed with the same key.
 */
object PhoneUpdates {
    private const val MAX_APK_BYTES = 400L * 1024 * 1024
    private val digestPattern = Regex("sha256:([a-fA-F0-9]{64})")
    private val versionPattern = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

    /** Release builds only: a debug build is a different app (app.gocast.debug). */
    val enabled: Boolean get() = !BuildConfig.DEBUG

    private val _installFailure = MutableStateFlow<String?>(null)
    /** Set by [UpdateInstallReceiver] when Android's installer reports a failure. */
    val installFailure: StateFlow<String?> = _installFailure.asStateFlow()

    internal fun reportInstallFailure(message: String) {
        _installFailure.value = message
    }

    fun clearInstallFailure() {
        _installFailure.value = null
    }

    /** Null when this is the newest version, or the release does not pass every check. */
    suspend fun check(): PhoneUpdate? = withContext(Dispatchers.IO) {
        val repository = BuildConfig.RELEASE_REPOSITORY
        val connection = open("https://api.github.com/repos/$repository/releases/latest")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val release = Json.parseToJsonElement(connection.inputStream.bufferedReader().readText()).jsonObject
            if (release.bool("draft") != false || release.bool("prerelease") != false) return@withContext null
            val tag = release.string("tag_name") ?: return@withContext null
            val latest = parseVersion(tag) ?: return@withContext null
            val current = parseVersion(BuildConfig.VERSION_NAME) ?: return@withContext null
            if (compareVersions(latest, current) <= 0) return@withContext null
            val version = latest.joinToString(".")
            val name = "gocast-$version.apk"
            val asset = release["assets"]?.jsonArray?.map { it.jsonObject }
                ?.singleOrNull { it.string("name") == name } ?: return@withContext null
            if (asset.string("state") != "uploaded") return@withContext null
            val size = asset["size"]?.jsonPrimitive?.longOrNull ?: return@withContext null
            if (size !in 1..MAX_APK_BYTES) return@withContext null
            val sha256 = digestPattern.matchEntire(asset.string("digest").orEmpty())
                ?.groupValues?.get(1)?.lowercase() ?: return@withContext null
            val url = "https://github.com/$repository/releases/download/$tag/$name"
            if (asset.string("browser_download_url") != url) return@withContext null
            PhoneUpdate(version, url, size, sha256)
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads (or reuses a verified earlier download) and checks size and SHA-256. */
    suspend fun download(context: Context, update: PhoneUpdate, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "gocast-${update.version}.apk")
            dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
            if (target.length() == update.size && sha256(target) == update.sha256) {
                return@withContext target
            }
            val partial = File(dir, target.name + ".part")
            // Follows GitHub's redirect to its download host (HTTPS to HTTPS).
            val connection = open(update.url)
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "GitHub answered ${connection.responseCode}"
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            done += read
                            check(done <= update.size) { "The download is larger than GitHub said" }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(done.toFloat() / update.size)
                        }
                    }
                }
                check(done == update.size) { "The download stopped early" }
                check(digest.digest().hex() == update.sha256) { "The download did not match GitHub's checksum" }
                check(partial.renameTo(target)) { "Could not save the download" }
                target
            } catch (e: Exception) {
                partial.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Hands the APK to Android; its own "Update this app?" screen follows via [UpdateInstallReceiver]. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("gocast.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                // Mutable: the installer adds the status and its confirmation screen.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "GoCast-Android/${BuildConfig.VERSION_NAME}")
        }

    private fun parseVersion(value: String): List<Int>? =
        versionPattern.matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int =
        a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
}
