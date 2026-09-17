plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.semver4j)
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.named<Task>("check") {
    dependsOn("detekt")
}
