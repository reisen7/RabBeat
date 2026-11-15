plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.serialization")
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    android {
        namespace = "com.rabbeat.app.core"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop")

    val jvmCommonMain = sourceSets.create("jvmCommonMain") {
        dependsOn(sourceSets.commonMain.get())
    }
    sourceSets.androidMain.get().dependsOn(jvmCommonMain)
    sourceSets["desktopMain"].dependsOn(jvmCommonMain)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("androidx.paging:paging-common:3.5.3")
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
        }
        jvmCommonMain.dependencies {
            implementation("javax.inject:javax.inject:1")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

