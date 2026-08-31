plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":gradle-plugin-tools-api"))
    implementation(project(":gradle-plugin-tools"))
    compileOnly(gradleApi())
}
