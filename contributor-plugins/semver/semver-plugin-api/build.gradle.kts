plugins {
    `java-library`
}

repositories { mavenCentral() }

dependencies {
    api(libs.semver4j)
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(project(":gradle-plugin-utils"))
    compileOnly(gradleApi())
}
