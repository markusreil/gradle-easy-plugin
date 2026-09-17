plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-plugin-core"))
    implementation(project(":contributor-plugins:semver:semver-plugin"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        create("semverTestHarness") {
            id = "com.mreil.easy.test.semver"
            implementationClass = "com.mreil.easy.semver.SemverTestHarnessPlugin"
        }
    }
}

testing {
    suites {
        // jvm-defaults registers/configures functionalTest (framework, main output, test kit and
        // plugin-under-test metadata); only the repo-specific helper projects are declared here.
        val functionalTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project(":gradle-plugin-testutils"))
                implementation(project(":easy-test-support"))
            }
        }
    }
}

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.named<Task>("check") {
    dependsOn("detekt")
}
