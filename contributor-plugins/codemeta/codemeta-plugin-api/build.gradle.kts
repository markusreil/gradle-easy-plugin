plugins {
    `java-library`
    alias(libs.plugins.kotlin.serialization)
}

repositories { mavenCentral() }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.semver4j)
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}
