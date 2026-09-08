plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
    api(libs.semver4j)
    implementation(project(":easy-contributor-api"))
    implementation(project(":gradle-plugin-utils"))
    compileOnly(gradleApi())
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.named<Task>("check") {
    dependsOn("detekt")
    dependsOn("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
