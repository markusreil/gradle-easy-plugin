plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(project(":gradle-plugin-utils"))
    testImplementation(project(":gradle-plugin-testutils"))
    testImplementation(project(":easy-test-support"))
    compileOnly(gradleApi())
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.named<Task>("check") {
    dependsOn("detekt")
}
