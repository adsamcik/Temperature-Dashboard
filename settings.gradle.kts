pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TemperatureDashboard"

// Application entry points
include(":app:android")
include(":app:desktop")

// Cross-platform glue
include(":shared")

// Core modules (pure-Kotlin, KMP-ready)
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")

// Decoder pipeline (lifted from BluetoothEvaluator, pure Kotlin)
include(":decoder:api")
include(":decoder:builtins")

// BLE abstraction
include(":ble:api")
include(":ble:android")
include(":ble:desktop")

// Feature modules
include(":feature:dashboard")
include(":feature:scan")
include(":feature:detail")
include(":feature:settings")
