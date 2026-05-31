plugins {
    alias(libs.plugins.tdash.jvm.library)
}

dependencies {
    implementation(project(":decoder:api"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
