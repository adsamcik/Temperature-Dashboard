plugins {
    alias(libs.plugins.tdash.android.application)
    alias(libs.plugins.tdash.android.application.compose)
}

private val appVersionName: String = file("$rootDir/VERSION").readText().trim()
private val appVersionCode: Int = run {
    val (major, minor, patch) = appVersionName.split('.').map { it.toInt() }
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.adsamcik.temperaturedashboard"

    defaultConfig {
        applicationId = "com.adsamcik.temperaturedashboard"
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Production signing: takes keystore from env vars/secrets when present
        // (set TDASH_RELEASE_KEYSTORE_PATH, TDASH_RELEASE_KEYSTORE_PASSWORD,
        // TDASH_RELEASE_KEY_ALIAS, TDASH_RELEASE_KEY_PASSWORD on CI).
        // Falls back to the debug signing config so the release APK is at
        // least installable for sideloading from GitHub Releases. The CI
        // notes call this out in the release body when the fallback is used.
        create("release") {
            val keystorePath = System.getenv("TDASH_RELEASE_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TDASH_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TDASH_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("TDASH_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use release signing if a keystore was provided, otherwise the
            // debug signing config (auto-created by AGP) so the APK is still
            // installable. CI distinguishes the two via TDASH_RELEASE_KEYSTORE_PATH.
            signingConfig = if (System.getenv("TDASH_RELEASE_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.startup)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.napier)
}
