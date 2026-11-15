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

// Same versioning as :app — latest git tag, -PversionName override wins.
val tagVersion = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText.get().trim().removePrefix("v")
}.getOrDefault("")

val desktopVersion = providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() }
    ?: tagVersion.ifBlank { "0.0.0-dev" }

// jpackage only accepts dotted-numeric versions; strip any suffix.
val numericVersion = Regex("""\d+\.\d+\.\d+""").find(desktopVersion)?.value ?: "0.0.0"

compose.desktop {
    application {
        mainClass = "com.rabbeat.app.desktop.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "RabBeat"
            packageVersion = numericVersion
            description = "RabBeat - local music player"
            vendor = "RabBeat"
            // Cap heap: a runaway list would OOM inside the app instead of
            // pressuring the whole machine.
            // No -Dfile.encoding=UTF-8: on GBK Windows it desyncs AWT tray
            // menus (PopupMenu labels encode via file.encoding → mojibake).
            jvmArgs("-Xmx1g", "-Drabbeat.version=$desktopVersion")

            windows {
                menuGroup = "RabBeat"
                upgradeUuid = "5f7d0c4e-2c8a-4f6e-9b1d-3a8e6c5d2f01"
                iconFile.set(project.file("packaging/rabbeat.ico"))
            }
        }
    }
}

// `./gradlew :desktopApp:run` also gets the version (nativeDistributions
// jvmArgs only reach the packaged launchers).
tasks.withType<JavaExec>().configureEach {
    systemProperty("rabbeat.version", desktopVersion)
}

// Portable zip of the jpackage app image (no installer, no admin needed).
tasks.register<Zip>("packagePortableZip") {
    group = "compose desktop"
    description = "Zip the composed app image as a portable package"
    dependsOn("createDistributable")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/RabBeat")
    from(appDir)
    archiveFileName.set("RabBeat-$desktopVersion-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
}
