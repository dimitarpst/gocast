import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Each app includes this project and compiles it with its existing Kotlin version.
// The JVM artifact also works on Android; no Android APIs or UI dependencies live here.
// Separate output directories let the Android and desktop builds run independently.
layout.buildDirectory.set(layout.projectDirectory.dir("build/${rootProject.name}"))

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}
