package com.mreil.easy.jvm

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JvmDefaultsPluginFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `sources and javadoc jars created together with main plugin`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val rootDir = File("/home/mreil/projects/mreil/gradle-easy-plugin-new")
        val coreJar = File(rootDir, "easy-plugin-core/build/libs/easy-plugin-core.jar").invariantSeparatorsPath
        val apiJar = File(rootDir, "gradle-plugin-tools-api/build/libs/gradle-plugin-tools-api.jar").invariantSeparatorsPath
        val jvmJar = File(rootDir, "contributor-plugins/jvm-defaults/build/libs/jvm-defaults.jar").invariantSeparatorsPath
        File(projectDir, "build.gradle.kts").writeText(
            """
            buildscript {
                dependencies {
                    classpath(files("$coreJar", "$apiJar", "$jvmJar"))
                }
            }
            import com.mreil.easy.ProjectPlugin
            plugins {
                `java-library`
            }
            apply<ProjectPlugin>()
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("sourcesJar", "javadocJar")
                .forwardOutput()
                .build()

        val libs = File(projectDir, "build/libs").listFiles()?.map { it.name } ?: emptyList()
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("sourcesJar")
            softly.assertThat(libs.any { it.contains("sources") }).isTrue()
            softly.assertThat(libs.any { it.contains("javadoc") }).isTrue()
        }
    }
}
