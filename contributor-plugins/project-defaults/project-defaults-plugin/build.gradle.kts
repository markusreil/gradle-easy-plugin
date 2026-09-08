plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

repositories { mavenCentral() }

dependencies {
    api(project(":contributor-plugins:project-defaults:project-defaults-plugin-api"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(project(":gradle-plugin-utils"))
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
                implementation(project(":easy-plugin-core"))
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
