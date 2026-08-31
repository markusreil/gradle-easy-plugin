package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JvmDefaultsFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `sources and javadoc jars via main plugin + jvm-defaults contributor`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.project")
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
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
