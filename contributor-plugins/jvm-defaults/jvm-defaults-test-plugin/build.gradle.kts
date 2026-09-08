plugins {
    `java-gradle-plugin`
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
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
