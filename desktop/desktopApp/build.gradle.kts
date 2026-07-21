import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.withType

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
            description = "Cast your Android phone screen to this PC over Wi-Fi"
            copyright = "Copyright 2026 Dimitar. MIT License."
            vendor = "govrse"
            licenseFile.set(rootProject.file("../LICENSE"))

            // Packaging icons only (runtime window icons live in src/main/resources/icon_*.png).
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "govrse"
                shortcut = true
                dirChooser = true
                // Stable across releases so MSI upgrades replace the previous install.
                upgradeUuid = "7E3A9C2B-5F14-4D8A-9B6E-1C0D4A8F2E75"
                msiPackageVersion = "1.0.0"
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
}
