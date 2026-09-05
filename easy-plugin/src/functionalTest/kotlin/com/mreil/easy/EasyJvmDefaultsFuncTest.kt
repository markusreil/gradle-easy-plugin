package com.mreil.easy

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyJvmDefaultsFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `sources and javadoc jars via main plugin + jvm-defaults contributor`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.project")
                }
                """.trimIndent(),
            )
        }

        val result =
            project.build("sourcesJar", "javadocJar")

        val libs = project.file("build/libs").listFiles()?.map { it.name } ?: emptyList()
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("sourcesJar")
            softly.assertThat(libs.any { it.contains("sources") }).isTrue()
            softly.assertThat(libs.any { it.contains("javadoc") }).isTrue()
        }
    }
}
