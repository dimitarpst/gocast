package app.gocast.update

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseCatalogTest {
    private val repository = "dimitarpst/gocast"
    private val name = "gocast-1.0.4-windows-x64-setup.exe"
    private val url = "https://github.com/$repository/releases/download/v1.0.4/$name"
    private val digest = "sha256:" + "a".repeat(64)

    private fun release(
        assetName: String = name,
        download: String = url,
        hash: String = digest,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ) = """{
        "tag_name":"v1.0.4", "draft":$draft, "prerelease":$prerelease,
        "body":"Release notes", "assets":[{
            "name":"$assetName", "state":"uploaded", "size":100,
            "digest":"$hash", "browser_download_url":"$download"
        }]
    }"""

    @Test fun numericOrderingHandlesDoubleDigitComponents() {
        assertTrue(checkNotNull(ReleaseVersion.parse("1.10.0")) > checkNotNull(ReleaseVersion.parse("1.9.99")))
        assertEquals(ReleaseVersion.parse("1.0.4"), ReleaseVersion.parse("v1.0.4"))
        assertFalse(checkNotNull(ReleaseVersion.parse("1.0.3")) > checkNotNull(ReleaseVersion.parse("1.0.4")))
    }

    @Test fun unstableOrMalformedVersionsAreNotStableReleases() {
        listOf("1.0.4-beta", "1.0", "1.0.4.1", "01.0.4", "1.0.4+build", "999999999999.0.0", " v1.0.4").forEach {
            assertNull(ReleaseVersion.parse(it), it)
        }
    }

    @Test fun choosesTheExactArchitectureAndReleaseAsset() {
        val result = ReleaseCatalog.parseLatest(release(), repository, "x64")
        assertEquals(ReleaseVersion(1, 0, 4), result.version)
        assertEquals(name, result.asset?.name)
        assertEquals("a".repeat(64), result.asset?.sha256)
        assertNull(ReleaseCatalog.parseLatest(release(), repository, "arm64").asset)
        assertNull(ReleaseCatalog.parseLatest(release(), repository, null).asset)
    }

    @Test fun neverAcceptsDraftOrPrereleaseMetadata() {
        assertFailsWith<IllegalArgumentException> { ReleaseCatalog.parseLatest(release(draft = true), repository, "x64") }
        assertFailsWith<IllegalArgumentException> { ReleaseCatalog.parseLatest(release(prerelease = true), repository, "x64") }
    }

    @Test fun rejectsForeignAndUnencryptedDownloadAddresses() {
        listOf(
            url.replace("https:", "http:"),
            url.replace("github.com", "github.com.example.org"),
            url.replace(repository, "someone/else"),
            url.replace("v1.0.4/", "v1.0.3/"),
            url.replace("github.com", "user@github.com"),
            "$url?redirect=elsewhere",
        ).forEach { bad ->
            assertNull(ReleaseCatalog.parseLatest(release(download = bad), repository, "x64").asset, bad)
        }
    }

    @Test fun rejectsMissingChecksumsAndAmbiguousLegacyNames() {
        assertNull(ReleaseCatalog.parseLatest(release(hash = ""), repository, "x64").asset)
        assertNull(ReleaseCatalog.parseLatest(release(hash = "sha256:abc"), repository, "x64").asset)
        assertNull(ReleaseCatalog.parseLatest(release(assetName = "gocast-1.0.4.msi"), repository, "x64").asset)
        assertNull(ReleaseCatalog.parseLatest(release(assetName = "gocast-1.0.4-windows-x64.msi"), repository, "x64").asset)
    }

    @Test fun rejectsOversizedOrUnfinishedUploads() {
        assertNull(ReleaseCatalog.parseLatest(release().replace("\"size\":100", "\"size\":2000000000"), repository, "x64").asset)
        assertNull(ReleaseCatalog.parseLatest(release().replace("\"uploaded\"", "\"new\""), repository, "x64").asset)
    }

    @Test fun redirectsStayOnEncryptedGithubDownloadHosts() {
        assertTrue(GitHubUpdates.allowedDownload(URI(url)))
        assertTrue(GitHubUpdates.allowedDownload(URI("https://release-assets.githubusercontent.com/asset?token=fixture")))
        listOf(
            "http://release-assets.githubusercontent.com/asset",
            "https://release-assets.githubusercontent.com.example.org/asset",
            "https://user@release-assets.githubusercontent.com/asset",
            "https://release-assets.githubusercontent.com:444/asset",
            "https://github.com/someone/else/releases/download/v1.0.4/setup.exe",
            "file:///C:/setup.exe",
        ).forEach { assertFalse(GitHubUpdates.allowedDownload(URI(it)), it) }
    }
}
