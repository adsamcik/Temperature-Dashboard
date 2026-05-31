plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.core.designsystem"
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            // Material 3 Expressive (FAB Menu, ButtonGroup, MotionScheme.expressive()) —
            // Android-only because Compose Multiplatform's material3 fork still tracks
            // androidx.compose.material3:1.3.x while Expressive is in 1.4 alphas.
            // Listed last so Gradle resolves to the higher version on Android.
            implementation(libs.androidx.compose.material3.expressive)
        }
    }
}
