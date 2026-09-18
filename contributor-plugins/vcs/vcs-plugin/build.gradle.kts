plugins {
    `java-library`
}

repositories { mavenCentral() }

dependencies {
    api(project(":contributor-plugins:vcs:vcs-plugin-api"))
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
                implementation(gradleTestKit())
            }
        }
    }
}
