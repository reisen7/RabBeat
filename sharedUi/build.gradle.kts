plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

compose.resources {
    packageOfResClass = "com.rabbeat.app.sharedui.generated.resources"
    publicResClass = true
}

kotlin {
    android {
        namespace = "com.rabbeat.app.sharedui"
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
        minSdk = 26
        // Off by default on a KMP library. Without it the compose string
        // table never lands in the APK, and the mini player crashes on open.
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
            api(project(":core"))
            api(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.17.0")
        }
    }
}
