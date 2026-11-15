plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        named("jvmMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core"))
                implementation(project(":sharedUi"))
                implementation("androidx.sqlite:sqlite-bundled:2.5.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                // Tag reading + javax.sound sample-rate/format SPIs for playback.
                implementation("org.jaudiotagger:jaudiotagger:2.0.1")
                implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
                implementation("org.jflac:jflac-codec:1.5.2")
                implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.rabbeat.app.desktop.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "RabBeat"
            packageVersion = "0.1.0"
            description = "RabBeat - local music player"
            vendor = "RabBeat"

            windows {
                menuGroup = "RabBeat"
                upgradeUuid = "5f7d0c4e-2c8a-4f6e-9b1d-3a8e6c5d2f01"
                iconFile.set(project.file("packaging/rabbeat.ico"))
            }
        }
    }
}
