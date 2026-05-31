plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.feature.scan"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ui"))
            api(project(":core:model"))
            implementation(project(":ble:api"))
            implementation(project(":decoder:api"))
            implementation(project(":core:database"))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
