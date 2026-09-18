package com.mreil.easy

import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Regression guard for the settings↔project classloader split: the plugin is loaded from the
 * settings classpath while KGP is resolved by the build's own `plugins {}` (a child classloader),
 * so `java.targetVersion` must wire Kotlin without a hard KGP link.
 */
@ExtendWith(GradleTestProjectExtension::class)
class KotlinTargetCompatibilityFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `pins kotlin jvmTarget and jdk release from java target version`() {
        val targetProbe =
            probeTask("verifyKotlinTarget") {
                prelude(
                    "val compileKotlin = project.tasks.named(" +
                        "\"compileKotlin\", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).get()",
                )
                expect("KOTLIN_JVM_TARGET", "compileKotlin.compilerOptions.jvmTarget.get().target", "11")
                expect(
                    "KOTLIN_JDK_RELEASE_COUNT",
                    "compileKotlin.compilerOptions.freeCompilerArgs.get().count { it == \"-Xjdk-release=11\" }",
                    "1",
                )
            }
        project.systemProperty("java.targetVersion", "11")
        project.configure { stageTargetProject(targetProbe.script()) }

        val result = project.build("verifyKotlinTarget")

        assertSoftly { softly ->
            targetProbe.assertOutput(softly, result.output)
        }
    }

    private fun GradleTestProject.stageTargetProject(probeScript: String) {
        settings(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$KGP_VERSION"
                }
            }
            plugins {
                id("com.mreil.easy.settings")
            }
            rootProject.name = "kotlin-target"
            extensions.configure<com.mreil.easy.EasyExtension>("easy") {
                extensions.configure<com.mreil.easy.jvm.EasyJvmDefaultsExtension>("jvmDefaults") {
                    enabled.set(true)
                }
            }
            """.trimIndent(),
        )
        file("codemeta.json", CODEMETA_JSON)
        file("src/main/kotlin/com/example/Placeholder.kt", KOTLIN_SOURCE)
        buildGradle(
            """
            buildscript {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            plugins {
                `java-library`
                id("org.jetbrains.kotlin.jvm")
            }
            repositories { mavenCentral() }
            $probeScript
            """.trimIndent(),
        )
    }

    private companion object {
        /** Pinned to the `kotlin` version in `gradle/libs.versions.toml`. */
        const val KGP_VERSION = "2.4.20"

        const val KOTLIN_SOURCE =
            "package com.example\n\n/** A placeholder. */\nclass Placeholder\n"

        val CODEMETA_JSON =
            """
            {
              "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
              "@type": "SoftwareSourceCode",
              "name": "test",
              "description": "test",
              "version": "1.0.0"
            }
            """.trimIndent()
    }
}
