plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":easy-contributor-api"))
    implementation(project(":gradle-plugin-utils"))
    compileOnly(gradleApi())
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.util=ALL-UNNAMED",
    )
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(gradleTestKit())
            }
        }
    }
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.named<Task>("check") {
    dependsOn("detekt")
}
