plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.core.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            api(project(":core:model"))
            api(libs.koalaplot.core)
            implementation(libs.napier)
        }
    }
}
