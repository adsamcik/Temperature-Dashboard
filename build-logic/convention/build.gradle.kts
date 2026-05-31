plugins {
    `kotlin-dsl`
}

group = "com.adsamcik.temperaturedashboard.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.compose.compiler.gradle.plugin)
    compileOnly(libs.compose.multiplatform.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tdash.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "tdash.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "tdash.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "tdash.kotlin.multiplatform"
            implementationClass = "KotlinMultiplatformConventionPlugin"
        }
        register("kotlinMultiplatformCompose") {
            id = "tdash.kotlin.multiplatform.compose"
            implementationClass = "KotlinMultiplatformComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "tdash.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("room") {
            id = "tdash.room"
            implementationClass = "RoomConventionPlugin"
        }
    }
}
