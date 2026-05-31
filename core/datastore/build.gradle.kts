plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.core.datastore"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.multiplatform.settings)
            api(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
