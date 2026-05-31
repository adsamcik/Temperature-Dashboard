plugins {
    alias(libs.plugins.tdash.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.ble.android"
}

dependencies {
    api(project(":ble:api"))
    api(project(":decoder:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.napier)
}
