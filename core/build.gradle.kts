plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("javax.inject:javax.inject:1")
    implementation("androidx.paging:paging-common:3.5.3")
}
