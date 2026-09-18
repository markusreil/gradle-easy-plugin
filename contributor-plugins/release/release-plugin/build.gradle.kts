plugins {
    `java-library`
}

easy {
    publish.enabled = false
}

repositories { mavenCentral() }

dependencies {
    api(project(":contributor-plugins:release:release-plugin-api"))
    implementation(project(":contributor-plugins:vcs:vcs-plugin-api"))
    implementation(project(":contributor-plugins:semver:semver-plugin-api"))
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
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(project(":easy-plugin-core"))
                implementation(project(":contributor-plugins:semver:semver-plugin"))
                implementation(project(":gradle-plugin-testutils"))
                implementation(gradleTestKit())
            }
        }
    }
}
