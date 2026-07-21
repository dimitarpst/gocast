import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.withType
import java.io.File

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

        nativeDistributions {
            // Windows release focus: MSI. Keep Deb/Dmg available for future.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "gocast"
            packageVersion = "1.0.0"
            // ASCII only — WiX/jpackage mangles fancy dashes and copyright symbols.
            description = "gocast — cast your Android phone screen to this PC over Wi-Fi"
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
                msiPackageVersion = "1.0.0"
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

afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        jvmArgs(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
        )
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            jvmArgs(
                "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }
    }

    // Ship uninstall wipe script into $APPDIR/resources (compose app resources).
    tasks.named<Sync>("prepareAppResources").configure {
        from(project.layout.projectDirectory.dir("package/windows")) {
            include("gocast-cleanup.cmd")
        }
    }

    fun injectUninstallCleanup(msi: File) {
        if (!msi.isFile) {
            logger.warn("gocast: MSI not found for uninstall cleanup inject")
            return
        }
        val script = project.file("package/windows/inject-uninstall-cleanup.js")
        if (!script.isFile) return
        val code = try {
            ProcessBuilder(
                "cscript.exe",
                "//nologo",
                script.absolutePath,
                msi.absolutePath,
            ).redirectErrorStream(true).start().waitFor()
        } catch (e: Exception) {
            logger.warn("gocast: uninstall cleanup inject failed: ${e.message}")
            return
        }
        if (code != 0) {
            logger.warn(
                "gocast: could not inject uninstall cleanup into ${msi.name} " +
                    "(exit $code). MSI still installs; wipe may need manual cleanup.",
            )
        } else {
            logger.lifecycle("gocast: injected uninstall cleanup into ${msi.name}")
        }
    }

    listOf("packageMsi", "packageReleaseMsi").forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            doLast {
                val msiDir = layout.buildDirectory
                    .dir("compose/binaries/main/msi")
                    .get()
                    .asFile
                val releaseMsiDir = layout.buildDirectory
                    .dir("compose/binaries/main-release/msi")
                    .get()
                    .asFile
                sequenceOf(msiDir, releaseMsiDir)
                    .filter { it.isDirectory }
                    .flatMap { it.listFiles()?.toList().orEmpty() }
                    .filter { it.extension.equals("msi", ignoreCase = true) }
                    .forEach { injectUninstallCleanup(it) }
            }
        }
    }
}
