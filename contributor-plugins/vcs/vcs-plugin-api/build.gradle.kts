plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
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
