package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsPluginFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `settings plugin creates easy extension on settings and copies to root project`() {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("com.mreil.easy.settings")
            }
            extensions.configure<com.mreil.easy.EasyExtension>("easy") {
                extensions.configure<com.mreil.easy.fixtures.DummyExtension>("dummy") {
                    message.set("fromSettings")
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            import com.mreil.easy.EasyExtension
            import com.mreil.easy.fixtures.DummyExtension

            tasks.register("verifyExtension") {
                doLast {
                    val ext = project.extensions.findByName("easy") as? EasyExtension
                    val dummy = ext?.extensions?.findByName("dummy") as? DummyExtension
                    println("HAS_ROOT_EXTENSION=" + (ext != null))
                    println("DUMMY_MESSAGE=" + dummy?.message?.get())
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
            softly.assertThat(result.output).contains("HAS_ROOT_EXTENSION=true")
            softly.assertThat(result.output).contains("DUMMY_MESSAGE=fromSettings")
        }
    }
}
