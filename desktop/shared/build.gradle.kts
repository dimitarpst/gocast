plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
}

val generateReleaseMetadata = tasks.register<Copy>("generateReleaseMetadata") {
    from(rootProject.file("../release.properties"))
    into(layout.buildDirectory.dir("generated/releaseResources"))
    rename { "gocast-release.properties" }
}

kotlin {
    jvm()

    sourceSets {
        getByName("jvmMain").resources.srcDir("../../branding/fonts/res/font")
        getByName("jvmMain").resources.srcDir(generateReleaseMetadata)
        commonMain.dependencies {
            implementation(project(":protocol"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies{
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.qrcodegen)
            implementation(libs.ktor.server.websockets)
            // Outbound connection to the signaling Worker (remote casting).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.webview.native)
            implementation(libs.jmdns)
        }
    }
}
