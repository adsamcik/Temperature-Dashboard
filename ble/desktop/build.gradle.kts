plugins {
    alias(libs.plugins.tdash.jvm.library)
}

dependencies {
    api(project(":ble:api"))
    api(project(":decoder:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.napier)
}
