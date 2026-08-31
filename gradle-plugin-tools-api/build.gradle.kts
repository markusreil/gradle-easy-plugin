plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
}
