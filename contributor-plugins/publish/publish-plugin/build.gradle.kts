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
    api(project(":contributor-plugins:publish:publish-plugin-api"))
    implementation(project(":contributor-plugins:semver:semver-plugin-api"))
    implementation(project(":contributor-plugins:codemeta:codemeta-plugin-api"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(project(":gradle-plugin-utils"))
    implementation(libs.kotlinx.serialization.json)
    compileOnly(gradleApi())
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(libs.jreleaser)
                implementation(project(":easy-plugin-core"))
                implementation(project(":contributor-plugins:semver:semver-plugin"))
                // Codemeta contributor on the test classpath so the SPI registers
                // EasyCodemetaExtension (defaults to enabled) — the central-path unit
                // tests in EasyPublishCentralTest would otherwise fail EasyJreleaserPlugin's
                // codemeta-required guard.
                implementation(project(":contributor-plugins:codemeta:codemeta-plugin"))
                implementation(project(":gradle-plugin-testutils"))
                implementation(gradleTestKit())
            }
        }
    }
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.named<Task>("check") {
    dependsOn("detekt")
}
