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

// Centralized Spotless config — single source for Kotlin formatting (leaf projects only;
// intermediate containers like :contributor-plugins have no build file/repositories,
// so Spotless can't resolve ktlint there)
subprojects {
    if (childProjects.isNotEmpty()) return@subprojects
    if (project.path != ":test-fixtures") {
        apply(plugin = "maven-publish")
    }
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
