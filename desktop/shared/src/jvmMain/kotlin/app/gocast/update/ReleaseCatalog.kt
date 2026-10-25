package app.gocast.update

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class UpdateAsset(val name: String, val url: URI, val size: Long, val sha256: String)
data class AvailableRelease(val version: ReleaseVersion, val notes: String, val asset: UpdateAsset?)

object ReleaseCatalog {
    const val MaximumInstallerBytes = 1_073_741_824L
    private val digestPattern = Regex("sha256:([a-fA-F0-9]{64})")

    fun architecture(osArch: String): String? = when (osArch.lowercase()) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> null
    }

    fun parseLatest(json: String, repository: String, architecture: String?): AvailableRelease {
        val release = Json.parseToJsonElement(json).jsonObject
        require(release["draft"]?.jsonPrimitive?.booleanOrNull == false &&
            release["prerelease"]?.jsonPrimitive?.booleanOrNull == false) {
            "GitHub did not return a published stable release"
        }
        val tag = requireNotNull(release.string("tag_name"))
        val version = requireNotNull(ReleaseVersion.parse(tag)) { "Unsupported release version" }
        val expectedNames = listOf(
            "gocast-$version-windows-$architecture-setup.exe",
        )
        val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        val asset = if (architecture == null) null else expectedNames.firstNotNullOfOrNull { name ->
            val matches = assets.filter { it.string("name") == name }
            if (matches.size != 1) null else parseAsset(matches.single(), repository, tag, name)
        }
        return AvailableRelease(version, release.string("body").orEmpty().take(16_000), asset)
    }

    private fun parseAsset(asset: JsonObject, repository: String, tag: String, name: String): UpdateAsset? {
        if (asset.string("state") != "uploaded") return null
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: return null
        if (size !in 1..MaximumInstallerBytes) return null
        val digest = digestPattern.matchEntire(asset.string("digest").orEmpty())
            ?.groupValues?.get(1)?.lowercase() ?: return null
        val uri = runCatching { URI(asset.string("browser_download_url")) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com" || uri.port != -1 ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            uri.path != "/$repository/releases/download/$tag/$name") return null
        return UpdateAsset(name, uri, size, digest)
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
