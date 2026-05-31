plugins {
    alias(libs.plugins.tdash.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
