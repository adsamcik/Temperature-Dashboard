import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

private val appVersionName: String = file("$rootDir/VERSION").readText().trim()

// macOS jpackage refuses a major version of 0; pin to 1.0.0 until v1 ships.
private val macPackageVersion: String = run {
    val (major, minor, patch) = appVersionName.split('.').map { it.toInt() }
    if (major == 0) "1.$minor.$patch" else appVersionName
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
            packageVersion = appVersionName
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
                packageVersion = macPackageVersion
                dmgPackageVersion = macPackageVersion
                packageBuildVersion = macPackageVersion
                dmgPackageBuildVersion = macPackageVersion
            }
        }
    }
}
