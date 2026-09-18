plugins {
    `java-library`
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    api(project(":contributor-plugins:semver:semver-plugin-api"))
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(project(":easy-plugin-core"))
                implementation(gradleTestKit())
            }
        }
    }
}
