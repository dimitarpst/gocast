package app.gocast.update

import java.util.Properties

object ReleaseInfo {
    private val properties = Properties().apply {
        val resource = checkNotNull(ReleaseInfo::class.java.getResourceAsStream("/gocast-release.properties")) {
            "Missing generated GoCast release metadata"
        }
        resource.use { load(it) }
    }

    val version = checkNotNull(ReleaseVersion.parse(properties.getProperty("version", "")))
    val repository = properties.getProperty("repository").also {
        check(it == "dimitarpst/gocast") { "Unexpected GoCast release repository" }
    }
    val releasesUrl = "https://github.com/$repository/releases"
    val isDevelopment = !System.getProperty("gocast.devBuild").isNullOrBlank() ||
        System.getProperty("jpackage.app-path").isNullOrBlank()
}
