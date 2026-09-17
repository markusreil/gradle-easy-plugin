plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
}
