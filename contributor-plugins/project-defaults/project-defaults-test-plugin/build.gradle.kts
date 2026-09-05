plugins {
    `java-gradle-plugin`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
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
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
        val functionalTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(gradleTestKit())
                implementation(project(":gradle-plugin-testutils"))
                implementation(project(":easy-test-support"))
                implementation(libs.assertj.core)
            }
            targets { all { testTask.configure { shouldRunAfter(test) } } }
        }
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

detekt { config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml")) }

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.named<Task>("check") {
    dependsOn(testing.suites.named("functionalTest"))
    dependsOn("detekt")
    dependsOn("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
