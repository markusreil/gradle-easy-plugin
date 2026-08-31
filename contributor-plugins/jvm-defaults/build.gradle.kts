plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":gradle-plugin-tools-api"))
    implementation(project(":gradle-plugin-tools"))
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
        val functionalTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project(":easy-plugin-core"))
                implementation(gradleTestKit())
                implementation(libs.assertj.core)
            }
            targets { all { testTask.configure { shouldRunAfter(test) } } }
        }
    }
}

tasks.named<Task>("check") {
    dependsOn(testing.suites.named("functionalTest"))
    dependsOn("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
