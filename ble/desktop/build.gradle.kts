plugins {
    alias(libs.plugins.tdash.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":ble:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.napier)
}
