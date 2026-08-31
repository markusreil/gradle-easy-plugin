package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginRegistryFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `dummy Project plugin registered via shared service is auto-applied`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            import com.mreil.easy.PluginRegistry
            import com.mreil.easy.fixtures.DummyProjectPlugin

            plugins {
                id("com.mreil.easy.project")
            }

            val registry = gradle.sharedServices.getRegistrations().getByName(PluginRegistry.NAME).service.get() as PluginRegistry
            registry.registerProjectPlugin(DummyProjectPlugin::class)
            DummyProjectPlugin().apply(project)

            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("dummyTask")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("DUMMY_APPLIED")
        }
    }
}
