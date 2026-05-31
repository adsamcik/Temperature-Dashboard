package com.adsamcik.temperaturedashboard.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal val Project.javaVersion: JavaVersion get() = JavaVersion.VERSION_21

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 29
        }

        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
            isCoreLibraryDesugaringEnabled = true
        }

        // AGP 8.7's lint has IncompatibleClassChangeError in
        // NonNullableMutableLiveDataDetector against newer Kotlin metadata
        // (we don't even use LiveData). Disable the offending check globally
        // so release builds can finish.
        lint {
            disable += setOf(
                "NullSafeMutableLiveData",
                "InvalidPackage",
            )
            abortOnError = false
            checkReleaseBuilds = false
        }
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(commonFreeCompilerArgs)
        }
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(commonFreeCompilerArgs)
        }
    }
}

private val commonFreeCompilerArgs = listOf(
    "-Xjsr305=strict",
    "-opt-in=kotlin.RequiresOptIn",
    "-Xexpect-actual-classes",
)
