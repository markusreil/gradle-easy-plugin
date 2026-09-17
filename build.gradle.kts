import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    `lifecycle-base`
    `jacoco-report-aggregation`
    `test-report-aggregation`
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

repositories {
    mavenCentral()
}

dependencies {
    subprojects.filter { it.childProjects.isEmpty() }.forEach { subproject ->
        jacocoAggregation(subproject)
        testReportAggregation(subproject)
    }
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("test")
        }
        val testAggregateTestReport by creating(AggregateTestReport::class) {
            testSuiteName.set("test")
        }
    }
}

tasks.named<JacocoReport>("testCodeCoverageReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("check") {
    dependsOn("testCodeCoverageReport")
    dependsOn("testAggregateTestReport")
}

// Build everything for Java 17 bytecode regardless of the JDK running Gradle (which may be 21).
// `jvmToolchain` sets the Java toolchain (source/target 17) AND aligns Kotlin's jvmTarget to 17,
// so no explicit sourceCompatibility/targetCompatibility/jvmTarget are needed. The Gradle daemon
// itself is unaffected — only the compile/test toolchain is pinned to 17 (auto-provisioned if absent).
// Version is single-sourced from gradle.properties (java.toolchainVersion).
subprojects {
    // Leaf-project block: Spotless config, detekt task wiring.
    if (childProjects.isNotEmpty()) return@subprojects
    apply(plugin = "com.diffplug.spotless")
    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            // Pinned in gradle/libs.versions.toml; do not rely on Spotless's implicit ktlint default.
            ktlint(libs.versions.ktlint.get())
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
    // detekt 2.x analyses with type resolution, one task per source set (detektMain, detektTest,
    // detektFunctionalTest, ...). Route the conventional `detekt` task — and therefore `check` —
    // through those type-aware tasks, and disable the plain task's own non-type-aware run so no
    // source is analysed twice. The dependency is resolved lazily when the task graph is built,
    // once every per-source-set task (including functionalTest) has been registered; a disabled
    // task's dependencies still execute, so `./gradlew detekt` and `./gradlew check` both run the
    // type-aware analysis.
    pluginManager.withPlugin("dev.detekt") {
        val plainDetekt = tasks.named("detekt")
        plainDetekt.configure {
            dependsOn(
                provider {
                    tasks.names.filter {
                        it.startsWith("detekt") &&
                            it != "detekt" &&
                            it != "detektGenerateConfig" &&
                            !it.endsWith("SourceSet") &&
                            !it.contains("Baseline")
                    }
                },
            )
        }
        plainDetekt.configure { enabled = false }
    }
    // jvm-defaults applies jacoco per project (0.0.112+); keep every report in both formats here
    // so the individual build files need no jacoco wiring at all.
    pluginManager.withPlugin("jacoco") {
        tasks.named<JacocoReport>("jacocoTestReport") {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}
