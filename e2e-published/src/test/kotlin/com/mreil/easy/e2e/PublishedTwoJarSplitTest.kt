package com.mreil.easy.e2e

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Published-consumer E2E for the two-jar split.
 *
 * Resolves the two published marker artifacts from local Maven repositories (staged by the publish
 * tasks this test depends on) and applies `com.mreil.easy.settings` in `settings.gradle.kts` and
 * `com.mreil.easy.project` in the root build as a normal versioned request, together with KGP and
 * `java.targetVersion=11`.
 *
 * This is the only guard that reproduces the settings/project classloader boundary: TestKit's
 * `withPluginClasspath()` flattens the plugin classpath. It regresses if the settings artifact
 * carries project/KGP-linked classes (the original `NoClassDefFoundError: KotlinJvmExtension`),
 * because the project entry point would then be resolved by the settings classloader.
 */
class PublishedTwoJarSplitTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `published markers apply both scopes and pin the kotlin target`() {
        writeSettings()
        writeRootBuild()
        writeSource()

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("-Djava.targetVersion=11", "verifyTargets", "--stacktrace")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("KOTLIN_JVM_TARGET=11")
            softly.assertThat(result.output).contains("KOTLIN_JDK_RELEASE=-Xjdk-release=11")
        }
    }

    private fun writeSettings() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven { url = uri("${requireProperty("e2e.projectRepo")}") }
                    maven { url = uri("${requireProperty("e2e.settingsRepo")}") }
                    gradlePluginPortal()
                    mavenCentral()
                }
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "${requireProperty("e2e.kgpVersion")}"
                }
            }
            plugins {
                id("com.mreil.easy.settings") version "${requireProperty("e2e.pluginVersion")}"
            }
            rootProject.name = "published-e2e"
            """.trimIndent() + "\n",
        )
    }

    private fun writeRootBuild() {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("org.jetbrains.kotlin.jvm")
                id("com.mreil.easy.project") version "${requireProperty("e2e.pluginVersion")}"
            }
            group = "com.example"
            version = "1.0.0"
            repositories { mavenCentral() }

            // Keep the guard focused on the classloader boundary: disable contributors that are not
            // under test (project-defaults still validates the group/version set above).
            easy {
                publish { enabled.set(false) }
                semver { enabled.set(false) }
                codemeta { enabled.set(false) }
                vcs { enabled.set(false) }
                release { enabled.set(false) }
            }

            val kotlinCompile =
                tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java)
            val jvmTarget = kotlinCompile.flatMap { it.compilerOptions.jvmTarget }
            val jdkRelease =
                kotlinCompile.map { task ->
                    task.compilerOptions.freeCompilerArgs.get().firstOrNull { it.startsWith("-Xjdk-release=") }
                }

            tasks.register("verifyTargets") {
                doLast {
                    println("KOTLIN_JVM_TARGET=" + jvmTarget.get().target)
                    println("KOTLIN_JDK_RELEASE=" + jdkRelease.get())
                }
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeSource() {
        projectDir.resolve("src/main/kotlin/com/example/Placeholder.kt").apply {
            parentFile.mkdirs()
            writeText("package com.example\n\nclass Placeholder\n")
        }
    }

    private fun requireProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Missing system property '$name' (set by e2e-published/build.gradle.kts)." }
}
