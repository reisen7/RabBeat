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
                implementation(libs.coil.compose)
                implementation("androidx.sqlite:sqlite-bundled:2.5.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("net.java.dev.jna:jna:5.6.0")
                implementation("io.github.vinceglb:filekit-dialogs:0.16.0")
                // Tag reading + javax.sound sample-rate/format SPIs for playback.
                implementation("org.jaudiotagger:jaudiotagger:2.0.1")
                implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
                implementation("org.jflac:jflac-codec:1.5.2")
                implementation(libs.zxing.core)
                implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
            }
        }
        named("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
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
            // These are left out of the jlink runtime otherwise.
            // java.net.http: NeteaseClient / update check.
            // jdk.httpserver: the phone-sync listener. Without it, 开始 throws
            // NoClassDefFoundError and the row never changes.
            modules("java.net.http", "jdk.crypto.ec", "jdk.httpserver")
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

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

// `./gradlew :desktopApp:run` also gets the version (nativeDistributions
// jvmArgs only reach the packaged launchers).
tasks.withType<JavaExec>().configureEach {
    systemProperty("rabbeat.version", desktopVersion)
    providers.gradleProperty("rabbeat.dataDir").orNull?.let {
        systemProperty("rabbeat.dataDir", it)
    }
    listOf("rabbeat.start", "rabbeat.theme", "rabbeat.window", "rabbeat.autoplay").forEach { p ->
        providers.gradleProperty(p).orNull?.let { systemProperty(p, it) }
    }
}

val jvmTestComp = kotlin.targets.getByName("jvm").compilations.getByName("test")

// Seeds a realistic demo library; -Prabbeat.dataDir overrides the output dir.
tasks.register<JavaExec>("seedDemoLibrary") {
    group = "demo"
    description = "Build a demo music library into build/demo-data (or -Prabbeat.dataDir)"
    dependsOn(jvmTestComp.compileTaskProvider)
    classpath = files(jvmTestComp.runtimeDependencyFiles, jvmTestComp.output.allOutputs)
    mainClass.set("com.rabbeat.app.desktop.player.DemoSeederKt")
    args(providers.gradleProperty("rabbeat.dataDir").orNull
        ?: layout.buildDirectory.dir("demo-data").get().asFile.absolutePath)
}

// createDistributable deletes this directory first. If a previous RabBeat.exe
// still has the runtime jars open, that delete fails with a generic Gradle
// error. Move it aside ourselves so the message says what to do.
val prepareAppImageDir = tasks.register("prepareAppImageDir") {
    group = "compose desktop"
    description = "Move aside a previous app image so packaging can replace it"
    doFirst {
        val dir = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        if (!dir.exists()) return@doFirst
        val trash = dir.parentFile.resolve("app-stale-${System.currentTimeMillis()}")
        if (!dir.renameTo(trash)) {
            throw GradleException(
                "打包目录被占用，先从托盘退出 RabBeat，再重新打包。\n${dir.absolutePath}",
            )
        }
        if (!trash.deleteRecursively()) {
            logger.warn("旧的打包目录没删掉，可以稍后手动删除：${trash.absolutePath}")
        }
    }
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    dependsOn(prepareAppImageDir)
}

// packageExe also runs jpackage. Keep it after the app image so the two
// don't write the shared runtime image at the same time.
tasks.matching { it.name == "packageExe" }.configureEach {
    mustRunAfter("createDistributable")
}

// Portable zip of the jpackage app image. Unzip and run RabBeat.exe.
tasks.register<Zip>("packagePortableZip") {
    group = "compose desktop"
    description = "Build a portable Windows zip with RabBeat.exe (no installer)"
    dependsOn("createDistributable")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/RabBeat")
    from(appDir)
    archiveFileName.set("RabBeat-$desktopVersion-windows-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    doFirst {
        val exe = appDir.get().asFile.resolve("RabBeat.exe")
        if (!exe.isFile) {
            throw GradleException("没有生成 RabBeat.exe：${exe.absolutePath}")
        }
    }
}

// WiX installer from packageExe. jpackage names it RabBeat-<numeric>.exe;
// publish a stable name that keeps the full version, including a prerelease suffix.
tasks.register("packageWindowsSetup") {
    group = "compose desktop"
    description = "Build the Windows installer and name it RabBeat-<version>-windows-setup.exe"
    dependsOn("packageExe")
    val exeDir = layout.buildDirectory.dir("compose/binaries/main/exe")
    val version = desktopVersion
    doLast {
        val dir = exeDir.get().asFile
        val produced = dir.walkTopDown().firstOrNull { file ->
            file.isFile &&
                file.extension.equals("exe", ignoreCase = true) &&
                !file.name.endsWith("-windows-setup.exe")
        } ?: throw GradleException("没有生成安装包：${dir.absolutePath}")
        val published = dir.resolve("RabBeat-$version-windows-setup.exe")
        if (produced.absoluteFile != published.absoluteFile) {
            produced.copyTo(published, overwrite = true)
        }
    }
}

tasks.register("packageWindows") {
    group = "compose desktop"
    description = "Build the portable zip and the Windows installer"
    dependsOn("packagePortableZip", "packageWindowsSetup")
}
