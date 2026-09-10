import com.diffplug.gradle.spotless.SpotlessExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(providers.gradleProperty("java.toolchainVersion").get().toInt())
        }
    }
    // Leaf-project block: Spotless config, detekt source wiring.
    if (childProjects.isNotEmpty()) return@subprojects
    apply(plugin = "com.diffplug.spotless")
    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
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
    // The detekt task defaults to main+test sources only — include every Kotlin source set
    // (functionalTest, testFixtures, ...) so `check` (which depends on detekt) guards all code.
    // NOTE: detektMain/detektTest (type-resolution rules) are deliberately NOT wired into check:
    // they are EXPERIMENTAL in detekt 1.x and crash analyzing some files under Kotlin 2.3
    // (e.g. EasyCodemetaPlugin.kt). Run them manually; the abstract-base @Suppress annotations
    // keep them clean when they do run. Revisit with detekt 2.x.
    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        tasks.named("detekt") {
            val sourceTask = this as org.gradle.api.tasks.SourceTask
            project.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().forEach { sourceSet ->
                (sourceSet.extensions.findByName("kotlin") as? org.gradle.api.file.SourceDirectorySet)
                    ?.srcDirs
                    ?.forEach { sourceTask.source(it) }
            }
        }
    }
}
