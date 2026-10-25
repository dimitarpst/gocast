package app.gocast.update

import app.gocast.platform.GocastDataDirs
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UpdateFailure(message: String) : IOException(message)

object GitHubUpdates {
    private const val MaximumMetadataBytes = 1_048_576

    suspend fun check(): AvailableRelease = withContext(Dispatchers.IO) {
        val uri = URI("https://api.github.com/repos/${ReleaseInfo.repository}/releases/latest")
        val connection = connection(uri)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (connection.responseCode) {
                200 -> Unit
                404 -> throw UpdateFailure("The release list isn't available. Try again later.")
                403, 429 -> throw UpdateFailure("GitHub is limiting update checks. Try again later.")
                else -> throw UpdateFailure("GitHub couldn't complete the update check. Try again later.")
            }
            val bytes = connection.inputStream.use { it.readNBytes(MaximumMetadataBytes + 1) }
            if (bytes.size > MaximumMetadataBytes) throw UpdateFailure("The release details couldn't be read.")
            runCatching {
                ReleaseCatalog.parseLatest(
                    bytes.toString(Charsets.UTF_8),
                    ReleaseInfo.repository,
                    if (System.getProperty("os.name").startsWith("Windows", true)) {
                        ReleaseCatalog.architecture(System.getProperty("os.arch"))
                    } else null,
                )
            }.getOrElse { throw UpdateFailure("The release details couldn't be read. Open Releases to check manually.") }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(asset: UpdateAsset, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(GocastDataDirs.root(), "updates").toPath()
        Files.createDirectories(directory)
        // Each attempt owns its files; a cancelled older download cannot delete a newer one.
        val attempt = Files.createTempDirectory(directory, "download-")
        val partial = attempt.resolve("installer.part")
        val destination = directory.resolve(asset.name)
        try {
            var uri = asset.url
            var response: HttpURLConnection? = null
            for (redirect in 0..5) {
                currentCoroutineContext().ensureActive()
                if (!allowedDownload(uri)) throw UpdateFailure("The download address wasn't recognised.")
                val next = connection(uri)
                val code = try { next.responseCode } catch (error: Exception) {
                    next.disconnect()
                    throw error
                }
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = next.getHeaderField("Location")
                    next.disconnect()
                    if (location == null || redirect == 5) throw UpdateFailure("The download couldn't be started.")
                    uri = uri.resolve(location)
                } else if (code == 200) {
                    response = next
                    break
                } else {
                    next.disconnect()
                    throw UpdateFailure("The download isn't available right now. Try again later.")
                }
            }
            val source = response ?: throw UpdateFailure("The download couldn't be started.")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            try {
                source.inputStream.use { input ->
                    Files.newOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > asset.size || total > ReleaseCatalog.MaximumInstallerBytes) {
                                throw UpdateFailure("The download didn't match the release. Try downloading again.")
                            }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (total * 100 / asset.size).toInt()
                            if (percent != lastPercent) {
                                onProgress(total.toFloat() / asset.size)
                                lastPercent = percent
                            }
                        }
                    }
                }
            } finally {
                source.disconnect()
            }
            if (total != asset.size || digest.digest().hex() != asset.sha256) {
                throw UpdateFailure("The download didn't match the release. Try downloading again.")
            }
            currentCoroutineContext().ensureActive()
            Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING)
            destination.toFile()
        } finally {
            // Never remove an already staged download when a later attempt fails.
            runCatching { Files.deleteIfExists(partial) }
            runCatching { Files.deleteIfExists(attempt) }
        }
    }

    suspend fun cachedDownload(asset: UpdateAsset): File? {
        val file = File(File(GocastDataDirs.root(), "updates"), asset.name)
        return try {
            verifyDownloaded(file, asset)
            file
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }

    suspend fun verifyDownloaded(file: File, asset: UpdateAsset): Unit = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() != asset.size) throw UpdateFailure("Download the update again before opening setup.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().hex() != asset.sha256) throw UpdateFailure("Download the update again before opening setup.")
    }

    fun openReleases() {
        Desktop.getDesktop().browse(URI(ReleaseInfo.releasesUrl))
    }

    fun openInstaller(file: File) {
        // Native file association hands off to setup; no shell command or forced app shutdown.
        Desktop.getDesktop().open(file)
    }

    private fun connection(uri: URI): HttpURLConnection =
        (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "GoCast/${ReleaseInfo.version}")
        }

    internal fun allowedDownload(uri: URI): Boolean =
        uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.host == "github.com" &&
                uri.path.startsWith("/${ReleaseInfo.repository}/releases/download/") ||
                uri.host == "release-assets.githubusercontent.com" || uri.host == "objects.githubusercontent.com")

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
