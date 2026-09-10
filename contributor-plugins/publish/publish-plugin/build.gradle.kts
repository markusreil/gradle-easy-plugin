plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
    api(project(":contributor-plugins:publish:publish-plugin-api"))
    implementation(project(":contributor-plugins:semver:semver-plugin-api"))
    implementation(project(":contributor-plugins:codemeta:codemeta-plugin-api"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(project(":gradle-plugin-utils"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    compileOnly(gradleApi())
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(libs.assertj.core)
                implementation(libs.jreleaser)
                implementation(project(":easy-plugin-core"))
                implementation(project(":contributor-plugins:semver:semver-plugin"))
                // Codemeta contributor on the test classpath so the SPI registers
                // EasyCodemetaExtension (defaults to enabled) — the central-path unit
                // tests in EasyPublishCentralTest would otherwise fail EasyJreleaserPlugin's
                // codemeta-required guard.
                implementation(project(":contributor-plugins:codemeta:codemeta-plugin"))
                implementation(gradleTestKit())
            }
        }
    }
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
