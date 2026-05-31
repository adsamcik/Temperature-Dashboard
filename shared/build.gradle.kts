plugins {
    alias(libs.plugins.tdash.kotlin.multiplatform)
    alias(libs.plugins.tdash.kotlin.multiplatform.compose)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adsamcik.temperaturedashboard.shared"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ui"))
            api(project(":core:model"))
            api(project(":core:database"))
            api(project(":core:datastore"))
            api(project(":core:designsystem"))
            api(project(":decoder:api"))
            api(project(":decoder:builtins"))
            api(project(":ble:api"))
            api(project(":feature:dashboard"))
            api(project(":feature:scan"))
            api(project(":feature:detail"))
            api(project(":feature:settings"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.napier)
        }
        androidMain.dependencies {
            api(project(":ble:android"))
            implementation(libs.koin.android)
            implementation(libs.androidx.navigation.compose)
        }
        val desktopMain by getting {
            dependencies {
                api(project(":ble:desktop"))
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.androidx.navigation.compose)
            }
        }
    }
}
