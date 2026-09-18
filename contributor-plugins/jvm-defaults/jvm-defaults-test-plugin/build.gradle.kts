plugins {
    `java-gradle-plugin`
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-plugin-core"))
    implementation(project(":contributor-plugins:jvm-defaults:jvm-defaults-plugin"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    implementation(libs.kotlin.jvm.plugin.marker)
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        create("jvmDefaultsTestHarness") {
            id = "com.mreil.easy.test.jvm"
            implementationClass = "com.mreil.easy.jvm.JvmDefaultsTestHarnessPlugin"
        }
    }
}

testing {
    suites {
        // jvm-defaults registers/configures functionalTest (framework, main output, test kit and
        // plugin-under-test metadata); only the repo-specific helper projects are declared here.
        register<JvmTestSuite>("functionalTest") {
            dependencies {
                implementation(project(":gradle-plugin-testutils"))
                implementation(project(":easy-test-support"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
