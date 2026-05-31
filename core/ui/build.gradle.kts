plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.core.ui"
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.adsamcik.temperaturedashboard.core.ui.resources"
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
