plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.core.designsystem"
}
