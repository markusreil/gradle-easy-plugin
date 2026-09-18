plugins {
    `java-gradle-plugin`
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-plugin-core"))
    implementation(project(":contributor-plugins:project-defaults:project-defaults-plugin"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        create("projectDefaultsTestHarness") {
            id = "com.mreil.easy.test.projectdefaults"
            implementationClass = "com.mreil.easy.projectdefaults.ProjectDefaultsTestHarnessPlugin"
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
