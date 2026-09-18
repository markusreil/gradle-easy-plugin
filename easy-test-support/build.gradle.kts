plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    api(project(":easy-contributor-api"))
    api(project(":gradle-plugin-testutils"))
    implementation(libs.junit.jupiter.api)
}
