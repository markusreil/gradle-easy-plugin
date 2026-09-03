package com.mreil.easy

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class PluginRegistryFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `dummy Project plugin registered via shared service is auto-applied`() {
        project.configure {
            buildGradle(
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
        }

        val result =
            project.build("dummyTask")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("DUMMY_APPLIED")
        }
    }
}
