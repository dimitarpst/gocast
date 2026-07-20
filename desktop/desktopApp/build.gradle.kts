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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "gocast"
            packageVersion = "1.0.0"
            // Packaging icons only (runtime window icons live in src/main/resources/icon_*.png).
            windows {
                iconFile.set(project.file("icons/icon.ico"))
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
