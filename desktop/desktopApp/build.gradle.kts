import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.withType
import java.io.File
import groovy.json.JsonOutput
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseInfo = Properties().apply {
    rootProject.file("../release.properties").reader().use { load(it) }
}
val releaseVersion = requireNotNull(releaseInfo.getProperty("version"))
require(releaseVersion.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
val versionParts = releaseVersion.split('.').map(String::toInt)
require(versionParts[0] in 0..255 && versionParts[1] in 0..255 && versionParts[2] in 0..65535) {
    "The version must fit Windows Installer's major.minor.build limits"
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "app.gocast.MainKt"
        // Wry/WebView2 (kdroidfilter composewebview) needs Panama/FFM native access.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            // HTTPS update checks need the EC provider even when jdeps cannot infer it.
            modules("jdk.crypto.ec")
            // Windows release focus: MSI. Keep Deb/Dmg available for future.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "gocast"
            packageVersion = releaseVersion
            // ASCII only — WiX/jpackage mangles fancy dashes and copyright symbols.
            description = "gocast - cast your Android phone screen to this PC over Wi-Fi"
            copyright = "Copyright 2026 gocast contributors. MIT License."
            vendor = "gocast"
            licenseFile.set(rootProject.file("../LICENSE"))

            // Packaging icons only (runtime window icons live in src/main/resources/icon_*.png).
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "gocast"
                shortcut = true
                dirChooser = true
                // Stable across releases so MSI upgrades replace the previous install.
                upgradeUuid = "7E3A9C2B-5F14-4D8A-9B6E-1C0D4A8F2E75"
                msiPackageVersion = releaseVersion
                installationPath = "gocast"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
            macOS {
                iconFile.set(project.file("icons/icon.png"))
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from("compose-desktop.pro")
        }
    }
}

// The local refresh script launches the same JVM/classpath as Compose's run task,
// without keeping a Gradle process alive just to host the desktop window.
tasks.register("prepareDevRun") {
    group = "application"
    description = "Build desktop classes and write the local development launcher."
    // Compose's run classpath uses the application JAR, not just the classes
    // directory. Build it explicitly so a previous run's JAR is never reused.
    dependsOn("jar", "prepareAppResources", configurations.named("runtimeClasspath"))
    dependsOn(provider { tasks.named<JavaExec>("run").get().classpath })
    notCompatibleWithConfigurationCache("Reads the configured Compose run task for a local launcher")
    doLast {
        val runTask = tasks.named<JavaExec>("run").get()
        val metadataDir = layout.buildDirectory.dir("dev-run").get().asFile.apply { mkdirs() }
        val launchDir = providers.gradleProperty("gocastDevRunDir").orNull
            ?.let { file(it) } ?: metadataDir.resolve("runtime")
        launchDir.mkdirs()
        val workspace = rootProject.rootDir.parentFile.canonicalFile.toPath()
        // A running JVM must never read JARs that the next refresh overwrites.
        val runtimeClasspath = runTask.classpath.files.mapIndexed { index, entry ->
            if (entry.canonicalFile.toPath().startsWith(workspace)) {
                val copy = launchDir.resolve("classpath/$index-${entry.name}")
                copy.parentFile.mkdirs()
                if (entry.isDirectory) entry.copyRecursively(copy, overwrite = true)
                else entry.copyTo(copy, overwrite = true)
                copy.absolutePath
            } else entry.absolutePath
        }.joinToString(File.pathSeparator)
        val argsFile = launchDir.resolve("java.args")
        fun quoteArg(value: String): String = "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n") + "\""
        argsFile.writeText(
            (runTask.allJvmArgs + listOf("-cp", runtimeClasspath, runTask.mainClass.get()))
                .joinToString("\n", transform = ::quoteArg) + "\n",
            Charsets.UTF_8,
        )
        val config = mapOf(
            "javaExecutable" to runTask.javaLauncher.get().executablePath.asFile.absolutePath,
            "argsFile" to argsFile.absolutePath,
            "workingDirectory" to runTask.workingDir.absolutePath,
            "environment" to runTask.environment.filterKeys { it.startsWith("WEBVIEW2_") },
        )
        metadataDir.resolve("launch.json").writeText(JsonOutput.toJson(config), Charsets.UTF_8)
        logger.lifecycle("Development launcher: ${metadataDir.resolve("launch.json")}")
    }
}

afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        jvmArgs(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            jvmArgs(
                "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }

        // WebView2 autoplay + dark clear color (Gradle run).
        environment(
            "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
            "--autoplay-policy=no-user-gesture-required",
        )
        environment("WEBVIEW2_DEFAULT_BACKGROUND_COLOR", "FF12141A")
    }

    // Ship explicit-uninstall network cleanup; personal data is never wiped.
    tasks.named<Sync>("prepareAppResources").configure {
        from(project.layout.projectDirectory.dir("package/windows")) {
            include("gocast-cleanup.cmd")
        }
    }

    fun configureMsiMaintenance(msi: File) {
        check(msi.isFile) { "GoCast MSI not found: $msi" }
        val script = project.file("package/windows/configure-msi.js")
        check(script.isFile) { "Missing GoCast MSI maintenance authoring script" }
        val process = ProcessBuilder(
                "cscript.exe",
                "//nologo",
                script.absolutePath,
                msi.absolutePath,
                releaseVersion,
            ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "GoCast MSI maintenance authoring failed:\n$output" }
        logger.lifecycle(output.trim())
    }

    listOf("packageMsi", "packageReleaseMsi").forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            doLast {
                val variant = if (taskName == "packageReleaseMsi") "main-release" else "main"
                val msi = layout.buildDirectory
                    .file("compose/binaries/$variant/msi/gocast-$releaseVersion.msi").get().asFile
                configureMsiMaintenance(msi)
            }
        }
    }
}
