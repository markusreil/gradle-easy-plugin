package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectPluginFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `project plugin creates easy extension on project`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            import com.mreil.easy.EasyExtension

            plugins {
                id("com.mreil.easy.project")
            }

            tasks.register("verifyExtension") {
                doLast {
                    val ext = project.extensions.findByName("easy")
                    println("HAS_EXTENSION=" + (ext != null))
                    println("IS_EASY_EXTENSION=" + (ext is EasyExtension))
                }
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("verifyExtension")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_EXTENSION=true")
            softly.assertThat(result.output).contains("IS_EASY_EXTENSION=true")
        }
    }
}
