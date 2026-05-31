import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.napier)
}

compose.desktop {
    application {
        mainClass = "com.adsamcik.temperaturedashboard.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Dmg,
            )
            packageName = "TemperatureDashboard"
            packageVersion = "0.1.0"
            description = "Universal Bluetooth temperature & humidity sensor dashboard"
            copyright = "© 2026 adsamcik"
            vendor = "adsamcik"

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "5e9e8c0a-7e7d-4f8c-9e0a-7e0a7e0a7e0a"
            }
            linux {
                shortcut = true
            }
            macOS {
                bundleID = "com.adsamcik.temperaturedashboard"
                // Apple wants MAJOR.MINOR.PATCH with MAJOR > 0; until v1 ships we pin 1.0.0.
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
                packageBuildVersion = "1.0.0"
                dmgPackageBuildVersion = "1.0.0"
            }
        }
    }
}
